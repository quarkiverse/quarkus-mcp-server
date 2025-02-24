package io.quarkiverse.mcp.server.runtime;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.invoke.Invoker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.FeatureManager;
import io.quarkiverse.mcp.server.FeatureManager.FeatureInfo;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.RequestId;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.runtime.FeatureArgument.Provider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.quarkus.virtual.threads.VirtualThreadsRecorder;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public abstract class FeatureManagerBase<RESULT, INFO extends FeatureManager.FeatureInfo> {

    protected final Vertx vertx;

    protected final ObjectMapper mapper;

    protected final ConnectionManager connectionManager;

    protected final ConcurrentMap<String, McpLogImpl> logs;

    protected final CurrentIdentityAssociation currentIdentityAssociation;

    protected FeatureManagerBase(Vertx vertx, ObjectMapper mapper, ConnectionManager connectionManager,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation) {
        this.vertx = vertx;
        this.mapper = mapper;
        this.connectionManager = connectionManager;
        this.logs = new ConcurrentHashMap<>();
        this.currentIdentityAssociation = currentIdentityAssociation.isResolvable() ? currentIdentityAssociation.get() : null;
    }

    public Future<RESULT> execute(String id, FeatureExecutionContext executionContext) throws McpException {
        FeatureInvoker<RESULT> invoker = getInvoker(id);
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

    record FeatureExecutionContext(ArgumentProviders argProviders, SecuritySupport securitySupport) {
    }

    protected Object wrapResult(Object ret, FeatureMetadata<?> metadata, ArgumentProviders argProviders) {
        return ret;
    }

    public Iterator<INFO> iterator() {
        return infoStream().sorted().iterator();
    }

    public Page<INFO> fetchPage(Cursor cursor, int pageSize) {
        if (isEmpty()) {
            return Page.empty();
        }
        if (size() <= pageSize) {
            // Pagination is not needed
            return new Page<>(infoStream().sorted().toList(), true);
        }
        List<INFO> result = infoStream()
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

    abstract Stream<INFO> infoStream();

    public abstract int size();

    public boolean isEmpty() {
        return size() < 1;
    }

    @SuppressWarnings("unchecked")
    protected Object[] prepareArguments(FeatureMetadata<?> metadata, ArgumentProviders argProviders) throws McpException {
        if (metadata.info().arguments().isEmpty()) {
            return new Object[0];
        }
        Object[] ret = new Object[metadata.info().arguments().size()];
        int idx = 0;
        for (FeatureArgument arg : metadata.info().arguments()) {
            if (arg.provider() == Provider.MCP_CONNECTION) {
                ret[idx] = argProviders.connection();
            } else if (arg.provider() == Provider.REQUEST_ID) {
                ret[idx] = new RequestId(argProviders.requestId());
            } else if (arg.provider() == Provider.REQUEST_URI) {
                ret[idx] = new RequestUri(argProviders.uri());
            } else if (arg.provider() == Provider.MCP_LOG) {
                ret[idx] = log(logKey(metadata), metadata.info().declaringClassName(), argProviders);
            } else {
                Object val = argProviders.getArg(arg.name());
                if (val == null && arg.required()) {
                    throw new McpException("Missing required argument: " + arg.name(), JsonRPC.INVALID_PARAMS);
                }
                if (val instanceof Map map) {
                    // json object
                    JavaType javaType = mapper.getTypeFactory().constructType(arg.type());
                    try {
                        ret[idx] = mapper.readValue(new JsonObject(map).encode(), javaType);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException(e);
                    }
                } else if (val instanceof List list) {
                    // json array
                    JavaType javaType = mapper.getTypeFactory().constructType(arg.type());
                    try {
                        ret[idx] = mapper.readValue(new JsonArray(list).encode(), javaType);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException(e);
                    }
                } else {
                    if (arg.type() instanceof Class clazz && clazz.isEnum()) {
                        ret[idx] = Enum.valueOf(clazz, val.toString());
                    } else {
                        ret[idx] = val;
                    }
                }
            }
            idx++;
        }
        return ret;
    }

    protected abstract FeatureInvoker<RESULT> getInvoker(String id);

    protected abstract McpException notFound(String id);

    protected Future<RESULT> execute(ExecutionModel executionModel, FeatureExecutionContext executionContext,
            Callable<Uni<RESULT>> action) {
        Promise<RESULT> ret = Promise.promise();
        ActivationSupport<RESULT> activation = new ActivationSupport<>(action, executionContext.securitySupport());

        Context context = VertxContext.getOrCreateDuplicatedContext(vertx);
        VertxContextSafetyToggle.setContextSafe(context, true);

        if (executionModel == ExecutionModel.VIRTUAL_THREAD) {
            // While counter-intuitive, we switch to a safe context, so that context is captured and attached
            // to the virtual thread.
            context.runOnContext(new Handler<Void>() {
                @Override
                public void handle(Void event) {
                    VirtualThreadsRecorder.getCurrent().execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                activation.call().subscribe().with(ret::complete, ret::fail);
                            } catch (Throwable e) {
                                ret.fail(e);
                            }
                        }
                    });
                }
            });
        } else if (executionModel == ExecutionModel.WORKER_THREAD) {
            context.executeBlocking(new Callable<Void>() {
                @Override
                public Void call() {
                    try {
                        activation.call().subscribe().with(ret::complete, ret::fail);
                    } catch (Throwable e) {
                        ret.fail(e);
                    }
                    return null;
                }
            }, false);
        } else {
            // Event loop
            context.runOnContext(new Handler<Void>() {
                @Override
                public void handle(Void event) {
                    try {
                        activation.call().subscribe().with(ret::complete, ret::fail);
                    } catch (Throwable e) {
                        ret.fail(e);
                    }
                }
            });
        }
        return ret.future();
    }

    protected McpLog log(String key, String loggerName, ArgumentProviders argProviders) {
        return logs.computeIfAbsent(key, k -> new McpLogImpl(argProviders.connection()::logLevel, loggerName, key,
                argProviders.responder()));
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

    private class ActivationSupport<T> implements Callable<Uni<T>> {

        private final Callable<Uni<T>> delegate;

        private final SecuritySupport securitySupport;

        private ActivationSupport(Callable<Uni<T>> delegate, SecuritySupport securitySupport) {
            this.delegate = delegate;
            this.securitySupport = securitySupport;
        }

        @Override
        public Uni<T> call() throws Exception {
            ManagedContext requestContext = Arc.container().requestContext();
            if (requestContext.isActive()) {
                if (securitySupport != null && currentIdentityAssociation != null) {
                    securitySupport.setCurrentIdentity(currentIdentityAssociation);
                }
                return delegate.call();
            } else {
                requestContext.activate();
                if (securitySupport != null && currentIdentityAssociation != null) {
                    securitySupport.setCurrentIdentity(currentIdentityAssociation);
                }
                try {
                    return delegate.call().eventually(requestContext::terminate);
                } catch (Throwable e) {
                    requestContext.terminate();
                    throw e;
                }
            }
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
            this.createdAt = Instant.now();
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
                Function<Object, Uni<RESPONSE>> resultMapper = metadata.resultMapper();
                Object ret = invoker.invoke(null, arguments);
                ret = wrapResult(ret, metadata, argProviders);
                return resultMapper.apply(ret);
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

        protected FeatureDefinitionBase(String name) {
            this.name = Objects.requireNonNull(name);
        }

        @SuppressWarnings("unchecked")
        protected THIS self() {
            return (THIS) this;
        }

        public THIS setDescription(String description) {
            this.description = Objects.requireNonNull(description);
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
            if (fun == null && asyncFun == null) {
                throw new IllegalStateException("Either sync or async logic must be set");
            }
            if (name == null) {
                throw new IllegalStateException("Name must be set");
            }
            if (description == null) {
                throw new IllegalStateException("Description must be set");
            }
        }

    }

    protected static abstract class FeatureDefinitionInfoBase<ARGUMENTS, RESPONSE>
            implements FeatureManager.FeatureInfo, FeatureInvoker<RESPONSE> {

        protected final String name;
        protected final String description;
        protected final Instant createdAt;
        protected final Function<ARGUMENTS, RESPONSE> fun;
        protected final Function<ARGUMENTS, Uni<RESPONSE>> asyncFun;
        protected final boolean runOnVirtualThread;

        protected FeatureDefinitionInfoBase(String name, String description, Function<ARGUMENTS, RESPONSE> fun,
                Function<ARGUMENTS, Uni<RESPONSE>> asyncFun, boolean runOnVirtualThread) {
            this.name = name;
            this.description = description;
            this.createdAt = Instant.now();
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

}
