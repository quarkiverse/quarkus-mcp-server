package io.quarkiverse.mcp.server.runtime;

import static io.quarkiverse.mcp.server.runtime.Messages.getParams;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.DefaultValueConverter;
import io.quarkiverse.mcp.server.ExecutionModel;
import io.quarkiverse.mcp.server.GlobalInputSchemaGenerator;
import io.quarkiverse.mcp.server.GlobalOutputSchemaGenerator;
import io.quarkiverse.mcp.server.Icon;
import io.quarkiverse.mcp.server.IconsProvider;
import io.quarkiverse.mcp.server.InputSchemaGenerator;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.Meta;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.OutputSchemaGenerator;
import io.quarkiverse.mcp.server.RequestId;
import io.quarkiverse.mcp.server.SupportedExecutionModels;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolFilter;
import io.quarkiverse.mcp.server.ToolInputGuardrail;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.ToolOutputGuardrail;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServersBuildTimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.quarkus.arc.All;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

@Singleton
public class ToolManagerImpl extends FeatureManagerBase<ToolResponse, ToolInfo> implements ToolManager {

    private static final Logger LOG = Logger.getLogger(ToolManagerImpl.class);

    private final GlobalInputSchemaGenerator globalInputSchemaGenerator;
    private final GlobalOutputSchemaGenerator globalOutputSchemaGenerator;

    private final McpServersRuntimeConfig config;

    final Instance<InputSchemaGenerator<?>> inputSchemaGenerator;
    final Instance<OutputSchemaGenerator> outputSchemaGenerator;

    final ConcurrentMap<String, ToolInfo> tools;

    final Map<Type, DefaultValueConverter<?>> defaultValueConverters;

    final List<ToolFilter> filters;

    final McpServersBuildTimeConfig buildTimeConfig;

    final Instance<ToolInputGuardrail> inputGuardrails;
    final Instance<ToolOutputGuardrail> outputGuardrails;
    final Instance<IconsProvider> iconsProviders;

    ToolManagerImpl(McpMetadata metadata,
            Vertx vertx,
            ObjectMapper mapper,
            ConnectionManager connectionManager,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation,
            ResponseHandlers responseHandlers,
            @All List<ToolFilter> filters,
            @Any Instance<ToolInputGuardrail> inputGuardrails,
            @Any Instance<ToolOutputGuardrail> outputGuardrails,
            @Any Instance<IconsProvider> iconsProviders,
            GlobalInputSchemaGenerator globalInputSchemaGenerator,
            GlobalOutputSchemaGenerator globalOutputSchemaGenerator,
            Instance<InputSchemaGenerator<?>> inputSchemaGenerator,
            Instance<OutputSchemaGenerator> outputSchemaGenerator,
            McpServersBuildTimeConfig buildTimeConfig,
            McpServersRuntimeConfig config) {
        super(vertx, mapper, connectionManager, currentIdentityAssociation, responseHandlers);
        this.tools = new ConcurrentHashMap<>();
        this.inputGuardrails = inputGuardrails;
        this.outputGuardrails = outputGuardrails;
        this.iconsProviders = iconsProviders;
        this.globalInputSchemaGenerator = globalInputSchemaGenerator;
        this.globalOutputSchemaGenerator = globalOutputSchemaGenerator;
        this.outputSchemaGenerator = outputSchemaGenerator;
        this.inputSchemaGenerator = inputSchemaGenerator;
        this.defaultValueConverters = metadata.defaultValueConverters();
        this.filters = filters;
        this.buildTimeConfig = buildTimeConfig;
        this.config = config;
        for (FeatureMetadata<ToolResponse> f : metadata.tools()) {
            this.tools.put(f.info().name(), new ToolMethod(f, iconsProviders));
        }
    }

    @Override
    Stream<ToolInfo> infos() {
        return tools.values().stream();
    }

    @Override
    Stream<ToolInfo> filter(Stream<ToolInfo> infos, McpConnection connection) {
        return infos.filter(t -> test(t, connection));
    }

    @Override
    public ToolInfo getTool(String name) {
        return tools.get(Objects.requireNonNull(name));
    }

    @Override
    public ToolDefinition newTool(String name) {
        if (tools.containsKey(name)) {
            throw toolAlreadyExists(name);
        }
        return new ToolDefinitionImpl(name);
    }

    IllegalArgumentException toolAlreadyExists(String name) {
        return new IllegalArgumentException("A tool with name [" + name + "] already exits");
    }

    @Override
    public ToolInfo removeTool(String name) {
        AtomicReference<ToolInfo> removed = new AtomicReference<>();
        tools.computeIfPresent(name, (key, value) -> {
            if (!value.isMethod()) {
                removed.set(value);
                notifyConnections("notifications/tools/list_changed");
                return null;
            }
            return value;
        });
        return removed.get();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected FeatureInvoker<ToolResponse> getInvoker(String id, McpRequest mcpRequest) {
        ToolInfo tool = tools.get(id);
        if (tool instanceof FeatureInvoker fi
                && matches(tool, mcpRequest)
                && test(tool, mcpRequest.connection())) {
            return fi;
        }
        return null;
    }

    @Override
    protected McpException notFound(String id) {
        return new McpException("Invalid tool name: " + id, JsonRpcErrorCodes.INVALID_PARAMS);
    }

    @Override
    protected Map<Type, DefaultValueConverter<?>> defaultValueConverters() {
        return defaultValueConverters;
    }

    @Override
    protected RuntimeException invalidArgument(FeatureMetadata<?> metadata, String message) {
        McpServerRuntimeConfig serverConfig = config.servers().get(metadata.info().serverName());
        if (serverConfig == null) {
            throw new IllegalStateException("Server config not found: " + metadata.info().serverName());
        }
        return switch (serverConfig.tools().inputValidationError()) {
            case TOOL -> new ToolCallException(message);
            case PROTOCOL -> new McpException(message, JsonRpcErrorCodes.INVALID_PARAMS);
            default -> throw new IllegalArgumentException("Unexpected value: " + serverConfig.tools().inputValidationError());
        };
    }

    private boolean test(ToolInfo tool, McpConnection connection) {
        if (filters.isEmpty() || connection == null) {
            return true;
        }
        for (ToolFilter filter : filters) {
            try {
                if (!filter.test(tool, connection)) {
                    return false;
                }
            } catch (RuntimeException e) {
                LOG.errorf(e, "Unable to apply filter: %s", filter);
            }
        }
        return true;
    }

    class ToolMethod extends FeatureMetadataInvoker<ToolResponse> implements ToolManager.ToolInfo {

        private final List<ToolInputGuardrail> input;
        private final List<ToolOutputGuardrail> output;

        private ToolMethod(FeatureMetadata<ToolResponse> metadata, Instance<IconsProvider> iconsProviders) {
            super(metadata, iconsProviders);
            this.input = initInputGuardrails(cast(metadata.info().inputGuardrails()));
            this.output = initOutputGuardrails(cast(metadata.info().outputGuardrails()));
        }

        @Override
        public Uni<JsonObject> beforeCall(FeatureExecutionContext context) {
            if (input.isEmpty()) {
                return super.beforeCall(context);
            }
            return toolBeforeCall(input, this, context);
        }

        @Override
        public Uni<ToolResponse> afterCall(FeatureExecutionContext context, ToolResponse response) {
            if (output.isEmpty()) {
                return Uni.createFrom().item(response);
            }
            return toolAfterCall(output, this, context, response);
        }

        @Override
        public String name() {
            return metadata.info().name();
        }

        @Override
        public String title() {
            return metadata.info().title();
        }

        @Override
        public String description() {
            return metadata.info().description();
        }

        @Override
        public String serverName() {
            return metadata.info().serverName();
        }

        @Override
        public Map<MetaKey, Object> metadata() {
            return metadata.info().metadata().entrySet()
                    .stream()
                    .collect(Collectors.toUnmodifiableMap(e -> MetaKey.from(e.getKey()), e -> Json.decodeValue(e.getValue())));
        }

        @Override
        public boolean isMethod() {
            return true;
        }

        @Override
        public Optional<ToolAnnotations> annotations() {
            return Optional.ofNullable(metadata.info().toolAnnotations());
        }

        @Override
        public List<ToolArgument> arguments() {
            return metadata.info().serializedArguments().stream()
                    .map(fa -> new ToolArgument(fa.name(), fa.description(), fa.required(), fa.type(), fa.defaultValue()))
                    .toList();
        }

        @Override
        public JsonObject asJson() {
            // TODO: it might make sense to cache the generated schemas
            JsonObject tool = metadata.asJson();
            ToolAnnotations toolAnnotations = metadata.info().toolAnnotations();
            if (toolAnnotations != null) {
                tool.put("annotations", new JsonObject()
                        .put("title", toolAnnotations.title())
                        .put("destructiveHint", toolAnnotations.destructiveHint())
                        .put("idempotentHint", toolAnnotations.idempotentHint())
                        .put("openWorldHint", toolAnnotations.openWorldHint())
                        .put("readOnlyHint", toolAnnotations.readOnlyHint()));
            }

            Class<? extends InputSchemaGenerator<?>> inputSchemaGeneratorClass = metadata.info().inputSchemaGenerator();
            Object inputSchema;
            if (inputSchemaGeneratorClass != null
                    && !inputSchemaGeneratorClass.equals(GlobalInputSchemaGenerator.class)) {
                inputSchema = inputSchemaGenerator.select(inputSchemaGeneratorClass)
                        .get()
                        .generate(this);
            } else {
                inputSchema = globalInputSchemaGenerator.generate(this).value();
            }
            tool.put("inputSchema", inputSchema);

            Class<? extends OutputSchemaGenerator> outputSchemaGeneratorClass = metadata.info().outputSchemaGenerator();
            if (outputSchemaGeneratorClass != null) {
                Object outputSchema;
                Class<?> outputSchemaFrom = metadata.info().outputSchemaFrom();
                if (GlobalOutputSchemaGenerator.class.equals(outputSchemaGeneratorClass)) {
                    outputSchema = globalOutputSchemaGenerator.generate(outputSchemaFrom);
                } else {
                    outputSchema = outputSchemaGenerator.select(outputSchemaGeneratorClass)
                            .get()
                            .generate(outputSchemaFrom);
                }
                tool.put("outputSchema", outputSchema);
            }

            if (iconsProvider != null) {
                try {
                    List<Icon> icons = iconsProvider.get(this);
                    tool.put("icons", icons);
                } catch (Exception e) {
                    LOG.errorf(e, "Unable to get icons for %s", name());
                }
            }

            return tool;
        }

    }

    private static abstract class ToolContext {

        private final ToolInfo tool;
        private final JsonObject message;
        private final McpRequest request;

        ToolContext(ToolInfo tool, JsonObject message, McpRequest request) {
            this.tool = tool;
            this.message = message;
            this.request = request;
        }

        public ToolInfo getTool() {
            return tool;
        }

        public McpConnection getConnection() {
            return request.connection();
        }

        public RequestId getRequestId() {
            return new RequestId(message.getValue("id"));
        }

        public Meta getMeta() {
            return MetaImpl.from(Messages.getParams(message));
        }

    }

    private static class ToolInputContextImpl extends ToolContext implements ToolInputGuardrail.ToolInputContext {

        private final AtomicReference<JsonObject> arguments;

        ToolInputContextImpl(ToolInfo tool, JsonObject message, McpRequest request) {
            super(tool, message, request);
            this.arguments = new AtomicReference<>();
            setArguments(Messages.getArguments(getParams(message)));
        }

        @Override
        public JsonObject getArguments() {
            return arguments.get();
        }

        @Override
        public void setArguments(JsonObject arguments) {
            JsonObject newArgs = new JsonObject(Map.copyOf(arguments.getMap()));
            this.arguments.set(newArgs);
        }

    }

    private static class ToolOutputContextImpl extends ToolContext implements ToolOutputGuardrail.ToolOutputContext {

        private final AtomicReference<ToolResponse> response;

        ToolOutputContextImpl(ToolInfo tool, JsonObject message, McpRequest request, ToolResponse response) {
            super(tool, message, request);
            this.response = new AtomicReference<>();
            setResponse(response);
        }

        @Override
        public ToolResponse getResponse() {
            return response.get();
        }

        @Override
        public void setResponse(ToolResponse response) {
            this.response.set(response);
        }

    }

    private List<ToolInputGuardrail> initInputGuardrails(List<Class<? extends ToolInputGuardrail>> classes) {
        if (classes == null || classes.isEmpty()) {
            return List.of();
        }
        List<ToolInputGuardrail> ret = new ArrayList<>();
        for (Class<? extends ToolInputGuardrail> clazz : classes) {
            @SuppressWarnings("unchecked")
            Instance<ToolInputGuardrail> child = (Instance<ToolInputGuardrail>) inputGuardrails
                    .select(clazz);
            if (child.isResolvable()) {
                ret.add(child.get());
            } else {
                try {
                    ret.add((ToolInputGuardrail) clazz.getConstructor().newInstance());
                } catch (Exception e) {
                    LOG.errorf(e, "Unable to instantiate input guardrail: %s", clazz);
                }
            }
        }
        return ret;
    }

    private List<ToolOutputGuardrail> initOutputGuardrails(List<Class<? extends ToolOutputGuardrail>> classes) {
        if (classes == null || classes.isEmpty()) {
            return List.of();
        }
        List<ToolOutputGuardrail> ret = new ArrayList<>();
        for (Class<? extends ToolOutputGuardrail> clazz : classes) {
            @SuppressWarnings("unchecked")
            Instance<ToolOutputGuardrail> child = (Instance<ToolOutputGuardrail>) outputGuardrails
                    .select(clazz);
            if (child.isResolvable()) {
                ret.add(child.get());
            } else {
                try {
                    ret.add((ToolOutputGuardrail) clazz.getConstructor().newInstance());
                } catch (Exception e) {
                    LOG.errorf(e, "Unable to instantiate output guardrail: %s", clazz);
                }
            }
        }
        return ret;
    }

    private static Uni<JsonObject> toolBeforeCall(List<ToolInputGuardrail> input, ToolInfo tool,
            FeatureExecutionContext context) {
        ToolInputContextImpl inputContext = new ToolInputContextImpl(tool, context.message(), context.mcpRequest());
        Iterator<ToolInputGuardrail> it = input.iterator();
        ToolInputGuardrail first = it.next();
        Uni<Void> uni = first.applyAsync(inputContext);
        while (it.hasNext()) {
            ToolInputGuardrail next = it.next();
            uni = uni.chain(args -> next.applyAsync(inputContext));
        }
        return uni.replaceWith(() -> inputContext.getArguments());
    }

    private static Uni<ToolResponse> toolAfterCall(List<ToolOutputGuardrail> output, ToolInfo tool,
            FeatureExecutionContext context, ToolResponse response) {
        ToolOutputContextImpl outputContext = new ToolOutputContextImpl(tool, context.message(), context.mcpRequest(),
                response);
        Iterator<ToolOutputGuardrail> it = output.iterator();
        ToolOutputGuardrail first = it.next();
        Uni<Void> uni = first.applyAsync(outputContext);
        while (it.hasNext()) {
            ToolOutputGuardrail next = it.next();
            uni = uni.chain(r -> next.applyAsync(outputContext));
        }
        return uni.replaceWith(() -> outputContext.getResponse());
    }

    class ToolDefinitionImpl
            extends FeatureManagerBase.FeatureDefinitionBase<ToolInfo, ToolArguments, ToolResponse, ToolDefinitionImpl>
            implements ToolManager.ToolDefinition {

        private String title;
        private final List<ToolArgument> arguments;
        private Object outputSchema;
        private Object inputSchema;
        private Map<MetaKey, Object> metadata = Map.of();
        private ToolAnnotations annotations;
        private List<Class<? extends ToolInputGuardrail>> inputGuardrails;
        private List<Class<? extends ToolOutputGuardrail>> outputGuardrails;

        private ToolDefinitionImpl(String name) {
            super(name);
            this.arguments = new ArrayList<>();
        }

        @Override
        public ToolDefinition addArgument(String name, String description, boolean required, Type type, String defaultValue) {
            arguments.add(new ToolArgument(name, description, required, type, defaultValue));
            return this;
        }

        @Override
        public ToolDefinition setAnnotations(ToolAnnotations annotations) {
            this.annotations = annotations;
            return this;
        }

        @Override
        public ToolDefinition setTitle(String title) {
            this.title = title;
            return this;
        }

        @Override
        public ToolDefinition generateOutputSchema(Class<?> from) {
            this.outputSchema = globalOutputSchemaGenerator.generate(from);
            return this;
        }

        @Override
        public ToolDefinition setOutputSchema(Object schema) {
            this.outputSchema = schema;
            return this;
        }

        @Override
        public ToolDefinition setInputSchema(Object schema) {
            this.inputSchema = schema;
            return this;
        }

        @Override
        public ToolDefinition setMetadata(Map<MetaKey, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        @Override
        public ToolDefinition setInputGuardrails(List<Class<? extends ToolInputGuardrail>> inputGuardrails) {
            this.inputGuardrails = inputGuardrails;
            return this;
        }

        @Override
        public ToolDefinition setOutputGuardrails(List<Class<? extends ToolOutputGuardrail>> outputGuardrails) {
            this.outputGuardrails = outputGuardrails;
            return this;
        }

        @Override
        public ToolInfo register() {
            validate();
            OptionalInt nameMaxLength = buildTimeConfig.servers().get(serverName).tools().nameMaxLength();
            if (nameMaxLength.isPresent() && name.length() > nameMaxLength.getAsInt()) {
                throw new IllegalStateException("Tool name [%s] exceeds the maximum length of %s characters"
                        .formatted(name, nameMaxLength.getAsInt()));
            }

            // Validate supported execution models if needed
            ExecutionModel model = runOnVirtualThread ? ExecutionModel.VIRTUAL_THREAD
                    : (fun != null ? ExecutionModel.WORKER_THREAD : ExecutionModel.EVENT_LOOP);
            if (inputGuardrails != null) {
                for (Class<? extends ToolInputGuardrail> clazz : inputGuardrails) {
                    if (!isModelSupported(model, clazz.getDeclaredAnnotation(SupportedExecutionModels.class))) {
                        throw new IllegalArgumentException(
                                "ToolInputGuardrail %s does not support the execution model: %s".formatted(clazz, model));
                    }
                }
            }
            if (outputGuardrails != null) {
                for (Class<? extends ToolOutputGuardrail> clazz : outputGuardrails) {
                    if (!isModelSupported(model, clazz.getDeclaredAnnotation(SupportedExecutionModels.class))) {
                        throw new IllegalArgumentException(
                                "ToolOutputGuardrail %s does not support the execution model: %s".formatted(clazz, model));
                    }
                }
            }

            ToolDefinitionInfo ret = new ToolDefinitionInfo(name, title, description, serverName, fun, asyncFun,
                    runOnVirtualThread, arguments, annotations, outputSchema, inputSchema, metadata,
                    initInputGuardrails(inputGuardrails), initOutputGuardrails(outputGuardrails), icons);
            ToolInfo existing = tools.putIfAbsent(name, ret);
            if (existing != null) {
                throw toolAlreadyExists(name);
            } else {
                notifyConnections(McpMessageHandler.NOTIFICATIONS_TOOLS_LIST_CHANGED);
            }
            return ret;
        }

        private boolean isModelSupported(ExecutionModel model, SupportedExecutionModels supportedModels) {
            if (supportedModels == null) {
                return true;
            }
            for (ExecutionModel supported : supportedModels.value()) {
                if (model == supported) {
                    return true;
                }
            }
            return false;
        }

    }

    class ToolDefinitionInfo extends FeatureManagerBase.FeatureDefinitionInfoBase<ToolArguments, ToolResponse>
            implements ToolManager.ToolInfo {

        private final String title;
        private final List<ToolArgument> arguments;
        private final Optional<ToolAnnotations> annotations;
        private final Object outputSchema;
        private final Object inputSchema;
        private final Map<MetaKey, Object> metadata;
        private final List<ToolInputGuardrail> input;
        private final List<ToolOutputGuardrail> output;

        private ToolDefinitionInfo(String name, String title, String description, String serverName,
                Function<ToolArguments, ToolResponse> fun,
                Function<ToolArguments, Uni<ToolResponse>> asyncFun, boolean runOnVirtualThread, List<ToolArgument> arguments,
                ToolAnnotations annotations,
                Object outputSchema, Object inputSchema, Map<MetaKey, Object> metadata, List<ToolInputGuardrail> input,
                List<ToolOutputGuardrail> output, List<Icon> icons) {
            super(name, description, serverName, fun, asyncFun, runOnVirtualThread, icons);
            this.title = title;
            this.arguments = List.copyOf(arguments);
            this.annotations = Optional.ofNullable(annotations);
            this.outputSchema = outputSchema;
            this.inputSchema = inputSchema;
            this.metadata = Map.copyOf(metadata);
            this.input = input;
            this.output = output;
        }

        @Override
        public String title() {
            return title;
        }

        @Override
        public List<ToolArgument> arguments() {
            return arguments;
        }

        @Override
        public Optional<ToolAnnotations> annotations() {
            return annotations;
        }

        @Override
        public Map<MetaKey, Object> metadata() {
            return metadata;
        }

        @Override
        public JsonObject asJson() {
            JsonObject tool = new JsonObject()
                    .put("name", name())
                    .put("description", description());
            if (title != null) {
                tool.put("title", title);
            }
            if (annotations.isPresent()) {
                tool.put("annotations", new JsonObject()
                        .put("title", annotations.get().title())
                        .put("destructiveHint", annotations.get().destructiveHint())
                        .put("idempotentHint", annotations.get().idempotentHint())
                        .put("openWorldHint", annotations.get().openWorldHint())
                        .put("readOnlyHint", annotations.get().readOnlyHint()));
            }
            if (inputSchema != null) {
                tool.put("inputSchema", inputSchema);
            } else {
                tool.put("inputSchema", globalInputSchemaGenerator.generate(this).value());
            }
            if (outputSchema != null) {
                tool.put("outputSchema", outputSchema);
            }
            if (!metadata.isEmpty()) {
                JsonObject meta = new JsonObject();
                for (Map.Entry<MetaKey, Object> e : metadata.entrySet()) {
                    meta.put(e.getKey().toString(), e.getValue());
                }
                tool.put("_meta", meta);
            }
            if (icons != null) {
                tool.put("icons", icons);
            }
            return tool;
        }

        @Override
        protected ToolArguments createArguments(ArgumentProviders argumentProviders) {
            Map<String, Object> args = argumentProviders.args();
            // Set default value if argument is missing
            for (ToolArgument a : arguments) {
                if (a.defaultValue() != null && !args.containsKey(a.name())) {
                    args.put(a.name(), convert(a.defaultValue(), a.type()));
                }
            }
            return new ToolArgumentsImpl(argumentProviders, args,
                    log(Feature.TOOL.toString().toLowerCase() + ":" + name, name, argumentProviders));
        }

        @Override
        public Uni<JsonObject> beforeCall(FeatureExecutionContext context) {
            if (input.isEmpty()) {
                return super.beforeCall(context);
            }
            return toolBeforeCall(input, this, context);
        }

        @Override
        public Uni<ToolResponse> afterCall(FeatureExecutionContext context, ToolResponse response) {
            if (output.isEmpty()) {
                return Uni.createFrom().item(response);
            }
            return toolAfterCall(output, this, context, response);
        }

    }

    static class ToolArgumentsImpl extends AbstractRequestFeatureArguments implements ToolArguments {

        private final Map<String, Object> args;

        private final McpLog log;

        ToolArgumentsImpl(ArgumentProviders argProviders, Map<String, Object> args, McpLog log) {
            super(argProviders);
            this.args = Map.copyOf(args);
            this.log = log;
        }

        @Override
        public McpLog log() {
            return log;
        }

        @Override
        public Map<String, Object> args() {
            return args;
        }

    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object obj) {
        return (T) obj;
    }

}
