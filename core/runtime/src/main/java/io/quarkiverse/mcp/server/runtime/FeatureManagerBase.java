package io.quarkiverse.mcp.server.runtime;

import java.lang.reflect.Type;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.invoke.Invoker;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.DefaultValueConverter;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.FeatureManager;
import io.quarkiverse.mcp.server.FeatureManager.FeatureInfo;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Meta;
import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.RawMessage;
import io.quarkiverse.mcp.server.RequestId;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Roots;
import io.quarkiverse.mcp.server.Sampling;
import io.quarkiverse.mcp.server.runtime.ResultMappers.Result;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.virtual.threads.VirtualThreadsRecorder;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public abstract class FeatureManagerBase<RESULT, INFO extends FeatureManager.FeatureInfo> {

    protected final Vertx vertx;

    protected final ObjectMapper mapper;

    protected final ConnectionManager connectionManager;

    protected final ConcurrentMap<String, Logger> loggers;

    protected final CurrentIdentityAssociation currentIdentityAssociation;

    final ResponseHandlers responseHandlers;

    protected FeatureManagerBase(Vertx vertx, ObjectMapper mapper, ConnectionManager connectionManager,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation, ResponseHandlers responseHandlers) {
        this.vertx = vertx;
        this.mapper = mapper;
        this.connectionManager = connectionManager;
        this.loggers = new ConcurrentHashMap<>();
        this.currentIdentityAssociation = currentIdentityAssociation.isResolvable() ? currentIdentityAssociation.get() : null;
        this.responseHandlers = responseHandlers;
    }

    public Future<RESULT> execute(String id, FeatureExecutionContext executionContext) throws McpException {
        FeatureInvoker<RESULT> invoker = getInvoker(id, executionContext.mcpRequest());
        if (invoker != null) {
            return execute(invoker.executionModel(), executionContext, new Callable<Uni<RESULT>>() {
                @Override
                public Uni<RESULT> call() throws Exception {
                    return invoker.call(executionContext.argProviders());
                }
            });
        }
        throw notFound(id);
    }

    record FeatureExecutionContext(ArgumentProviders argProviders, McpRequest mcpRequest) {
    }

    protected Object wrapResult(Object ret, FeatureMetadata<?> metadata, ArgumentProviders argProviders) {
        return ret;
    }

    public Iterator<INFO> iterator() {
        return infos().sorted().iterator();
    }

    public Page<INFO> fetchPage(McpRequest mcpRequest, Cursor cursor, int pageSize) {
        long count = infosForRequest(mcpRequest).count();
        if (count == 0) {
            return Page.empty();
        }
        if (pageSize <= 0 || count <= pageSize) {
            // Pagination is disabled/not needed
            return new Page<>(infosForRequest(mcpRequest).sorted().toList(), true);
        }
        List<INFO> result = infosForRequest(mcpRequest)
                .filter(r -> r.createdAt().isAfter(cursor.createdAt())
                        && (cursor.name() == null
                                || r.name().compareTo(cursor.name()) > 0))
                .sorted()
                // (pageSize + 1) so that we know if a next page exists
                .limit(pageSize + 1)
                .toList();
        if (result.size() > pageSize) {
            return new Page<>(result.subList(0, result.size() - 1), false);
        }
        return new Page<>(result, true);
    }

    Stream<INFO> infosForRequest(McpRequest mcpRequest) {
        return filter(infos().filter(i -> matches(i, mcpRequest)), mcpRequest.connection());
    }

    /**
     *
     * @return the stream of all infos
     */
    abstract Stream<INFO> infos();

    /**
     * @param connection (may be {@code null})
     * @return the stream of accesible infos
     */
    Stream<INFO> filter(Stream<INFO> infos, McpConnection connection) {
        return infos;
    }

    public boolean hasInfos(McpRequest mcpRequest) {
        return infosForRequest(mcpRequest).count() > 0;
    }

    protected boolean matches(INFO info, McpRequest mcpRequest) {
        return info.serverName().equals(mcpRequest.serverName());
    }

    protected Object[] prepareArguments(FeatureMetadata<?> metadata, ArgumentProviders argProviders) throws McpException {
        if (metadata.info().arguments().isEmpty()) {
            return new Object[0];
        }
        Object[] ret = new Object[metadata.info().arguments().size()];
        int idx = 0;
        for (FeatureArgument arg : metadata.info().arguments()) {
            ret[idx] = switch (arg.provider()) {
                case MCP_CONNECTION -> argProviders.connection();
                case REQUEST_ID -> new RequestId(argProviders.requestId());
                case REQUEST_URI -> new RequestUri(argProviders.uri());
                case MCP_LOG -> log(logKey(metadata), metadata.info().declaringClassName(), argProviders);
                case PROGRESS -> ProgressImpl.from(argProviders);
                case ROOTS -> RootsImpl.from(argProviders);
                case SAMPLING -> SamplingImpl.from(argProviders);
                case CANCELLATION -> CancellationImpl.from(argProviders);
                case RAW_MESSAGE -> RawMessageImpl.from(argProviders);
                case COMPLETE_CONTEXT -> CompleteContextImpl.from(argProviders);
                case META -> MetaImpl.from(argProviders.rawMessage().getJsonObject("params"));
                case ELICITATION -> ElicitationImpl.from(argProviders);
                case PARAMS -> handleParam(arg, argProviders.getArg(arg.name()));
                default -> throw new IllegalArgumentException("Unexpected argument provider: " + arg.provider());
            };
            idx++;
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    private Object handleParam(FeatureArgument arg, Object val) {
        if (val == null) {
            if (arg.defaultValue() != null) {
                // Try to use the default value
                val = convert(arg.defaultValue(), arg.type());
            }
            if (val == null && arg.required()) {
                throw new McpException("Missing required argument: " + arg.name(), JsonRpcErrorCodes.INVALID_PARAMS);
            }
        } else if (!arg.isValid(val)) {
            throw new McpException(
                    "Invalid argument [%s] - value does not match %s".formatted(arg.name(), arg.type().getTypeName()),
                    JsonRpcErrorCodes.INVALID_PARAMS);
        } else {
            if (val instanceof Map map) {
                // json object
                JavaType javaType = mapper.getTypeFactory().constructType(arg.type());
                try {
                    val = mapper.convertValue(map, javaType);
                } catch (IllegalArgumentException e) {
                    throw new McpException(
                            "Invalid argument [%s] - unable to convert JSON object to %s".formatted(arg.name(),
                                    arg.type().getTypeName()),
                            JsonRpcErrorCodes.INVALID_PARAMS);
                }
            } else if (val instanceof List list) {
                // json array
                JavaType javaType = mapper.getTypeFactory().constructType(arg.type());
                try {
                    val = mapper.convertValue(list, javaType);
                } catch (IllegalArgumentException e) {
                    throw new McpException(
                            "Invalid argument [%s] - unable to convert JSON array to %s".formatted(arg.name(),
                                    arg.type().getTypeName()),
                            JsonRpcErrorCodes.INVALID_PARAMS);
                }
            } else if (arg.type() instanceof Class clazz && clazz.isEnum()) {
                try {
                    val = Enum.valueOf(clazz, val.toString());
                } catch (IllegalArgumentException e) {
                    throw new McpException(
                            "Invalid argument [%s] - %s is not an enum constant of %s".formatted(arg.name(), val,
                                    clazz.getName()),
                            JsonRpcErrorCodes.INVALID_PARAMS);
                }
            } else if (val instanceof Number num) {
                val = coerceNumber(num, arg.type());
            }
        }

        if (arg.isOptional()) {
            val = Optional.ofNullable(val);
        }
        return val;
    }

    private Object coerceNumber(Number num, Type argType) {
        if (Integer.class.equals(argType) || int.class.equals(argType)) {
            return num instanceof Integer ? num : num.intValue();
        } else if (Long.class.equals(argType) || long.class.equals(argType)) {
            return num instanceof Long ? num : num.longValue();
        } else if (Short.class.equals(argType) || short.class.equals(argType)) {
            return num instanceof Short ? num : num.shortValue();
        } else if (Byte.class.equals(argType) || byte.class.equals(argType)) {
            return num instanceof Byte ? num : num.byteValue();
        } else if (Float.class.equals(argType) || float.class.equals(argType)) {
            return num instanceof Float ? num : num.floatValue();
        } else if (Double.class.equals(argType) || double.class.equals(argType)) {
            return num instanceof Double ? num : num.doubleValue();
        }
        return num;
    }

    protected abstract FeatureInvoker<RESULT> getInvoker(String id, McpRequest mcpRequest);

    protected abstract McpException notFound(String id);

    protected Future<RESULT> execute(ExecutionModel executionModel, FeatureExecutionContext executionContext,
            Callable<Uni<RESULT>> action) {
        Promise<RESULT> ret = Promise.promise();
        if (executionModel == ExecutionModel.VIRTUAL_THREAD) {
            VirtualThreadsRecorder.getCurrent().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        action.call().subscribe().with(ret::complete, ret::fail);
                    } catch (Throwable e) {
                        ret.fail(e);
                    }
                }
            });
        } else if (executionModel == ExecutionModel.WORKER_THREAD) {
            vertx.executeBlocking(new Callable<Void>() {
                @Override
                public Void call() {
                    try {
                        action.call().subscribe().with(ret::complete, ret::fail);
                    } catch (Throwable e) {
                        ret.fail(e);
                    }
                    return null;
                }
            }, false);
        } else {
            // Event loop - operation is always executed on a new duplicated context
            try {
                action.call().subscribe().with(ret::complete, ret::fail);
            } catch (Throwable e) {
                ret.fail(e);
            }
        }
        return ret.future();
    }

    protected McpLog log(String key, String loggerName, ArgumentProviders argProviders) {
        Logger logger = loggers.computeIfAbsent(key, k -> Logger.getLogger(loggerName));
        return new McpLogImpl(argProviders.connection()::logLevel, logger, key, argProviders.sender());
    }

    private String logKey(FeatureMetadata<?> metadata) {
        return metadata.feature().toString().toLowerCase() + ":" + metadata.info().name();
    }

    protected void notifyConnections(String method) {
        // Notify all connections
        for (McpConnectionBase c : connectionManager) {
            c.send(Messages.newNotification(method));
        }
    }

    interface FeatureInvoker<R> {

        ExecutionModel executionModel();

        Uni<R> call(ArgumentProviders argProviders);

    }

    class FeatureMetadataInvoker<RESPONSE> implements FeatureInvoker<RESPONSE> {

        protected final FeatureMetadata<RESPONSE> metadata;

        private final Instant createdAt;

        FeatureMetadataInvoker(FeatureMetadata<RESPONSE> metadata) {
            this.metadata = metadata;
            this.createdAt = nextTimestamp();
        }

        @Override
        public ExecutionModel executionModel() {
            return metadata.executionModel();
        }

        public Instant createdAt() {
            return createdAt;
        }

        @Override
        public Uni<RESPONSE> call(ArgumentProviders argProviders) {
            Invoker<Object, Object> invoker = metadata.invoker();
            Object[] arguments = prepareArguments(metadata, argProviders);
            try {
                Function<Result<Object>, Uni<RESPONSE>> resultMapper = metadata.resultMapper();
                Object ret = invoker.invoke(null, arguments);
                ret = wrapResult(ret, metadata, argProviders);
                return resultMapper.apply(new Result<>(ret, argProviders.serverName()));
            } catch (Throwable e) {
                return Uni.createFrom().failure(e);
            }
        }

    }

    protected static abstract class FeatureDefinitionBase<INFO extends FeatureInfo, ARGUMENTS, RESPONSE, THIS extends FeatureDefinitionBase<INFO, ARGUMENTS, RESPONSE, THIS>> {

        protected final String name;
        protected String description;
        protected Function<ARGUMENTS, RESPONSE> fun;
        protected Function<ARGUMENTS, Uni<RESPONSE>> asyncFun;
        protected boolean runOnVirtualThread;
        protected String serverName;

        protected FeatureDefinitionBase(String name) {
            this.name = Objects.requireNonNull(name);
            this.serverName = McpServer.DEFAULT;
        }

        @SuppressWarnings("unchecked")
        protected THIS self() {
            return (THIS) this;
        }

        public THIS setDescription(String description) {
            this.description = Objects.requireNonNull(description);
            return self();
        }

        public THIS setServerName(String serverName) {
            this.serverName = Objects.requireNonNull(serverName);
            return self();
        }

        public THIS setHandler(Function<ARGUMENTS, RESPONSE> fun, boolean runOnVirtualThread) {
            this.fun = Objects.requireNonNull(fun);
            this.runOnVirtualThread = runOnVirtualThread;
            return self();
        }

        public THIS setAsyncHandler(Function<ARGUMENTS, Uni<RESPONSE>> asyncFun) {
            this.asyncFun = Objects.requireNonNull(asyncFun);
            return self();
        }

        protected void validate() {
            validate(true);
        }

        protected void validate(boolean requireDescription) {
            if (fun == null && asyncFun == null) {
                throw new IllegalStateException("Either sync or async logic must be set");
            }
            if (name == null) {
                throw new IllegalStateException("Name must be set");
            }
            if (requireDescription && description == null) {
                throw new IllegalStateException("Description must be set");
            }
        }

    }

    protected static abstract class FeatureDefinitionInfoBase<ARGUMENTS, RESPONSE>
            implements FeatureManager.FeatureInfo, FeatureInvoker<RESPONSE> {

        protected final String name;
        protected final String description;
        protected final String serverName;
        protected final Instant createdAt;
        protected final Function<ARGUMENTS, RESPONSE> fun;
        protected final Function<ARGUMENTS, Uni<RESPONSE>> asyncFun;
        protected final boolean runOnVirtualThread;

        protected FeatureDefinitionInfoBase(String name, String description, String serverName,
                Function<ARGUMENTS, RESPONSE> fun,
                Function<ARGUMENTS, Uni<RESPONSE>> asyncFun, boolean runOnVirtualThread) {
            this.name = name;
            this.description = description;
            this.serverName = serverName;
            this.createdAt = nextTimestamp();
            this.fun = fun;
            this.asyncFun = asyncFun;
            this.runOnVirtualThread = runOnVirtualThread;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public String serverName() {
            return serverName;
        }

        @Override
        public boolean isMethod() {
            return false;
        }

        @Override
        public Instant createdAt() {
            return createdAt;
        }

        @Override
        public ExecutionModel executionModel() {
            if (runOnVirtualThread) {
                return ExecutionModel.VIRTUAL_THREAD;
            }
            return fun != null ? ExecutionModel.WORKER_THREAD : ExecutionModel.EVENT_LOOP;
        }

        protected abstract ARGUMENTS createArguments(ArgumentProviders argumentProviders);

        @Override
        public Uni<RESPONSE> call(ArgumentProviders argumentProviders) {
            Uni<RESPONSE> ret;
            ARGUMENTS args = createArguments(argumentProviders);
            if (fun != null) {
                ret = Uni.createFrom().item(fun.apply(args));
            } else {
                ret = asyncFun.apply(args);
            }
            return ret;
        }

    }

    protected Map<Type, DefaultValueConverter<?>> defaultValueConverters() {
        return Map.of();
    }

    Object convert(String value, Type type) {
        return convert(value, type, defaultValueConverters());
    }

    static Object convert(String value, Type type, Map<Type, DefaultValueConverter<?>> defaultValueConverters) {
        if (String.class.equals(type)) {
            return value;
        }
        type = box(type);
        DefaultValueConverter<?> converter = defaultValueConverters.get(type);
        if (converter != null) {
            return converter.convert(value);
        }
        if (type instanceof Class clazz) {
            if (clazz.isEnum()) {
                for (Object constant : clazz.getEnumConstants()) {
                    if (constant.toString().equalsIgnoreCase(value)) {
                        return constant;
                    }
                }
            }
        }
        throw new IllegalArgumentException(
                "Unable to convert the default value for argument type [" + type
                        + "] - provide a custom converter implementation");
    }

    static Type box(Type type) {
        if (type instanceof Class clazz) {
            if (!clazz.isPrimitive()) {
                return type;
            } else if (clazz.equals(Boolean.TYPE)) {
                return Boolean.class;
            } else if (clazz.equals(Character.TYPE)) {
                return Character.class;
            } else if (clazz.equals(Byte.TYPE)) {
                return Byte.class;
            } else if (clazz.equals(Short.TYPE)) {
                return Short.class;
            } else if (clazz.equals(Integer.TYPE)) {
                return Integer.class;
            } else if (clazz.equals(Long.TYPE)) {
                return Long.class;
            } else if (clazz.equals(Float.TYPE)) {
                return Float.class;
            } else if (clazz.equals(Double.TYPE)) {
                return Double.class;
            }
        }
        return type;
    }

    private static volatile Instant lastTimestamp = Instant.EPOCH;

    private synchronized static Instant nextTimestamp() {
        Instant ts = Instant.now();
        if (ts.isAfter(lastTimestamp)) {
            lastTimestamp = ts;
            return ts;
        }
        ts = lastTimestamp.plus(1, ChronoUnit.MILLIS);
        lastTimestamp = ts;
        return ts;
    }

    static abstract class AbstractFeatureArguments implements FeatureManager.FeatureArguments {

        protected final ArgumentProviders argProviders;

        AbstractFeatureArguments(ArgumentProviders argProviders) {
            this.argProviders = argProviders;
        }

        @Override
        public McpConnection connection() {
            return argProviders.connection();
        }

        @Override
        public Roots roots() {
            return RootsImpl.from(argProviders);
        }

        @Override
        public Sampling sampling() {
            return SamplingImpl.from(argProviders);
        }

        @Override
        public Elicitation elicitation() {
            return ElicitationImpl.from(argProviders);
        }

        @Override
        public RawMessage rawMessage() {
            return RawMessageImpl.from(argProviders);
        }

        @Override
        public Meta meta() {
            return MetaImpl.from(argProviders.rawMessage().getJsonObject("params"));
        }

    }

    static abstract class AbstractRequestFeatureArguments extends AbstractFeatureArguments
            implements FeatureManager.RequestFeatureArguments {

        AbstractRequestFeatureArguments(ArgumentProviders argProviders) {
            super(argProviders);
        }

        @Override
        public RequestId requestId() {
            return new RequestId(argProviders.requestId());
        }

        @Override
        public Progress progress() {
            return ProgressImpl.from(argProviders);
        }

        @Override
        public Cancellation cancellation() {
            return CancellationImpl.from(argProviders);
        }

    }

}
