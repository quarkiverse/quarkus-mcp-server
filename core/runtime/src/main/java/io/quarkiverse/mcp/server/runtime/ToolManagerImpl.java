package io.quarkiverse.mcp.server.runtime;

import static io.quarkiverse.mcp.server.runtime.Messages.getParams;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.DefaultValueConverter;
import io.quarkiverse.mcp.server.ExecutionModel;
import io.quarkiverse.mcp.server.Feature;
import io.quarkiverse.mcp.server.FilterContext;
import io.quarkiverse.mcp.server.GlobalGuardrail;
import io.quarkiverse.mcp.server.GlobalInputSchemaGenerator;
import io.quarkiverse.mcp.server.GlobalOutputSchemaGenerator;
import io.quarkiverse.mcp.server.Icon;
import io.quarkiverse.mcp.server.IconsProvider;
import io.quarkiverse.mcp.server.InputSchemaGenerator;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.McpProtocolVersion;
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
import io.quarkiverse.mcp.server.TransportHint;
import io.quarkiverse.mcp.server.runtime.config.McpServerBuildTimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServersBuildTimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.quarkus.arc.All;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import tools.jackson.databind.ObjectMapper;

@Singleton
public class ToolManagerImpl extends FeatureManagerBase<ToolResponse, ToolInfo> implements ToolManager {

    private static final Logger LOG = Logger.getLogger(ToolManagerImpl.class);

    private final GlobalInputSchemaGenerator globalInputSchemaGenerator;
    private final GlobalOutputSchemaGenerator globalOutputSchemaGenerator;

    private final McpServersRuntimeConfig config;

    final Instance<InputSchemaGenerator<?>> inputSchemaGenerator;
    final Instance<OutputSchemaGenerator> outputSchemaGenerator;

    final ConcurrentMap<FeatureKey, ToolInfo> tools;

    final Map<Type, DefaultValueConverter<?>> defaultValueConverters;

    final List<ToolFilter> filters;

    final McpServersBuildTimeConfig buildTimeConfig;

    final InjectableInstance<ToolInputGuardrail> inputGuardrails;
    final InjectableInstance<ToolOutputGuardrail> outputGuardrails;
    final Instance<IconsProvider> iconsProviders;

    // Global guardrails are shared by all tools
    final GlobalGuardrails<ToolInputGuardrail> globalInputGuardrails;
    final GlobalGuardrails<ToolOutputGuardrail> globalOutputGuardrails;

    final Event<ToolAdded> toolAddedEvent;
    final Event<ToolRemoved> toolRemovedEvent;

    ToolManagerImpl(McpMetadata metadata,
            Vertx vertx,
            ObjectMapper mapper,
            ConnectionManager connectionManager,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation,
            ServerRequests serverRequests,
            CancellationRequests cancellationRequests,
            @All List<ToolFilter> filters,
            @Any InjectableInstance<ToolInputGuardrail> inputGuardrails,
            @Any InjectableInstance<ToolOutputGuardrail> outputGuardrails,
            @All @GlobalGuardrail List<InstanceHandle<ToolInputGuardrail>> globalInputGuardrails,
            @All @GlobalGuardrail List<InstanceHandle<ToolOutputGuardrail>> globalOutputGuardrails,
            @Any Instance<IconsProvider> iconsProviders,
            GlobalInputSchemaGenerator globalInputSchemaGenerator,
            GlobalOutputSchemaGenerator globalOutputSchemaGenerator,
            Instance<InputSchemaGenerator<?>> inputSchemaGenerator,
            Instance<OutputSchemaGenerator> outputSchemaGenerator,
            McpServersBuildTimeConfig buildTimeConfig,
            McpServersRuntimeConfig config,
            Event<ToolAdded> toolAddedEvent,
            Event<ToolRemoved> toolRemovedEvent) {
        super(vertx, mapper, connectionManager, currentIdentityAssociation, serverRequests, cancellationRequests,
                config,
                metadata);
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
        this.toolAddedEvent = toolAddedEvent;
        this.toolRemovedEvent = toolRemovedEvent;
        this.globalInputGuardrails = new GlobalGuardrails<>(globalInputGuardrails);
        this.globalOutputGuardrails = new GlobalGuardrails<>(globalOutputGuardrails);
        for (FeatureMetadata<ToolResponse> f : metadata.tools()) {
            ToolMethod toolMethod = new ToolMethod(f, iconsProviders);
            for (String server : f.info().serverNames()) {
                this.tools.put(new FeatureKey(f.info().name(), server), toolMethod);
            }
        }
    }

    @Override
    Stream<ToolInfo> infos() {
        return tools.values().stream().distinct();
    }

    @Override
    Stream<ToolInfo> filter(Stream<ToolInfo> infos, FilterContext filterContext) {
        return infos.filter(t -> test(t, filterContext));
    }

    @Override
    protected McpMethod mcpListMethod() {
        return McpMethod.TOOLS_LIST;
    }

    @Override
    public ToolInfo getTool(String name, String serverName) {
        return tools.get(new FeatureKey(name, serverName));
    }

    @Override
    public ToolInfo getTool(String name) {
        return findUniqueByName(tools, name, Feature.TOOL);
    }

    @Override
    public ToolDefinition newTool(String name) {
        return new ToolDefinitionImpl(name);
    }

    IllegalArgumentException toolAlreadyExists(String name, String serverName) {
        return new IllegalArgumentException(
                "A tool with name [" + name + "] already exists for server configuration [" + serverName + "]");
    }

    @Override
    public ToolInfo removeTool(String name, String serverName) {
        FeatureKey key = new FeatureKey(name, serverName);
        AtomicReference<ToolInfo> removed = new AtomicReference<>();
        tools.computeIfPresent(key, (k, value) -> {
            if (!value.isMethod()) {
                removed.set(value);
                if (shouldNotifyListChanged(value)) {
                    notifyConnections(McpMethod.NOTIFICATIONS_TOOLS_LIST_CHANGED, Set.of(serverName));
                }
                return null;
            }
            return value;
        });
        ToolInfo removedTool = removed.get();
        if (removedTool != null) {
            toolRemovedEvent.fire(new ToolRemoved(removedTool));
        }
        return removedTool;
    }

    @Override
    public ToolInfo removeTool(String name) {
        AtomicReference<ToolInfo> removed = new AtomicReference<>();
        tools.entrySet().removeIf(e -> {
            if (e.getKey().name().equals(name) && !e.getValue().isMethod()) {
                removed.set(e.getValue());
                return true;
            }
            return false;
        });
        ToolInfo removedTool = removed.get();
        if (removedTool != null) {
            if (shouldNotifyListChanged(removedTool)) {
                notifyConnections(McpMethod.NOTIFICATIONS_TOOLS_LIST_CHANGED, removedTool.serverNames());
            }
            toolRemovedEvent.fire(new ToolRemoved(removedTool));
        }
        return removedTool;
    }

    @Override
    public void notifyListChanged(Predicate<McpConnection> filter) {
        notifyConnections(McpMethod.NOTIFICATIONS_TOOLS_LIST_CHANGED, Objects.requireNonNull(filter));
    }

    @SuppressWarnings("unchecked")
    @Override
    protected FeatureInvoker<ToolResponse> getInvoker(String id, McpRequest mcpRequest, JsonObject message) {
        ToolInfo tool = tools.get(new FeatureKey(id, mcpRequest.serverName()));
        if (tool instanceof FeatureInvoker fi
                && test(tool, FilterContextImpl.of(McpMethod.TOOLS_CALL, message, mcpRequest))) {
            return fi;
        }
        return null;
    }

    @Override
    protected boolean transformsExecutionFailure() {
        return true;
    }

    @Override
    protected Uni<ToolResponse> transformExecutionFailure(Throwable failure) {
        if (failure instanceof ToolCallException tce) {
            // Business logic error should result in ToolResponse with isError:true
            return Uni.createFrom().item(ToolResponse.error(tce.getMessage()));
        }
        return super.transformExecutionFailure(failure);
    }

    @Override
    protected McpException notFound(String id, McpProtocolVersion version) {
        return new McpException("Invalid tool name: " + id, JsonRpcErrorCodes.INVALID_PARAMS);
    }

    @Override
    protected Map<Type, DefaultValueConverter<?>> defaultValueConverters() {
        return defaultValueConverters;
    }

    @Override
    protected RuntimeException invalidArgument(FeatureMetadata<?> metadata, String serverName, String message) {
        McpServerRuntimeConfig serverConfig = config.servers().get(serverName);
        if (serverConfig == null) {
            throw new IllegalStateException("Server config not found: " + serverName);
        }
        return switch (serverConfig.tools().inputValidationError()) {
            case TOOL -> new ToolCallException(message);
            case PROTOCOL -> new McpException(message, JsonRpcErrorCodes.INVALID_PARAMS);
            default -> throw new IllegalArgumentException("Unexpected value: " + serverConfig.tools().inputValidationError());
        };
    }

    /**
     * A tool that belongs to several servers caches its schemas only if caching is enabled for all of them.
     */
    private boolean cacheSchemas(Set<String> serverNames) {
        for (String serverName : serverNames) {
            McpServerRuntimeConfig serverConfig = config.servers().get(serverName);
            if (serverConfig != null && !serverConfig.tools().cacheSchemas()) {
                return false;
            }
        }
        return true;
    }

    private boolean test(ToolInfo tool, FilterContext filterContext) {
        if (filters.isEmpty()) {
            return true;
        }
        for (ToolFilter filter : filters) {
            try {
                if (!filter.test(tool, filterContext)) {
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

        // Cached schemas; null if caching is disabled (or, for the output schema, if the tool declares none)
        private final LazyValue<Object> inputSchema;
        private final LazyValue<Object> outputSchema;

        private ToolMethod(FeatureMetadata<ToolResponse> metadata, Instance<IconsProvider> iconsProviders) {
            super(metadata, iconsProviders);
            this.input = allInputGuardrails(cast(metadata.info().inputGuardrails()));
            this.output = allOutputGuardrails(cast(metadata.info().outputGuardrails()));
            boolean cache = cacheSchemas(metadata.info().serverNames());
            this.inputSchema = cache ? new LazyValue<>(this::generateInputSchema) : null;
            this.outputSchema = cache && metadata.info().outputSchemaGenerator() != null
                    ? new LazyValue<>(this::generateOutputSchema)
                    : null;
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
        public Set<String> serverNames() {
            return metadata.info().serverNames();
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
        public Object inputSchema() {
            return inputSchema != null ? inputSchema.get() : generateInputSchema();
        }

        @Override
        public Object outputSchema() {
            return outputSchema != null ? outputSchema.get() : generateOutputSchema();
        }

        private Object generateInputSchema() {
            Class<? extends InputSchemaGenerator<?>> inputSchemaGeneratorClass = metadata.info().inputSchemaGenerator();
            if (inputSchemaGeneratorClass != null
                    && !inputSchemaGeneratorClass.equals(GlobalInputSchemaGenerator.class)) {
                return inputSchemaGenerator.select(inputSchemaGeneratorClass)
                        .get()
                        .generate(this);
            }
            return globalInputSchemaGenerator.generate(this).value();
        }

        private Object generateOutputSchema() {
            Class<? extends OutputSchemaGenerator> outputSchemaGeneratorClass = metadata.info().outputSchemaGenerator();
            if (outputSchemaGeneratorClass == null) {
                return null;
            }
            Type outputSchemaFrom = metadata.info().outputSchemaFrom();
            if (GlobalOutputSchemaGenerator.class.equals(outputSchemaGeneratorClass)) {
                return globalOutputSchemaGenerator.generate(outputSchemaFrom);
            }
            return outputSchemaGenerator.select(outputSchemaGeneratorClass)
                    .get()
                    .generate(outputSchemaFrom);
        }

        @Override
        public JsonObject asJson() {
            JsonObject tool = metadata.asJson();
            ToolAnnotations toolAnnotations = metadata.info().toolAnnotations();
            if (toolAnnotations != null) {
                tool.put("annotations", toolAnnotationsToJson(toolAnnotations));
            }
            tool.put("inputSchema", inputSchema());
            Object schema = outputSchema();
            if (schema != null) {
                tool.put("outputSchema", schema);
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

    private static JsonObject toolAnnotationsToJson(ToolAnnotations toolAnnotations) {
        JsonObject ret = new JsonObject();
        if (toolAnnotations != null) {
            if (toolAnnotations.title() != null && !toolAnnotations.title().isBlank()) {
                ret.put("title", toolAnnotations.title());
            }
            ret.put("destructiveHint", toolAnnotations.destructiveHint())
                    .put("idempotentHint", toolAnnotations.idempotentHint())
                    .put("openWorldHint", toolAnnotations.openWorldHint())
                    .put("readOnlyHint", toolAnnotations.readOnlyHint());
        }
        return ret;
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
            return new RequestId(Messages.getId(message));
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
            JsonObject args = Messages.getArguments(getParams(message));
            setArguments(args != null ? args : new JsonObject());
        }

        @Override
        public JsonObject getArguments() {
            return arguments.get();
        }

        @Override
        public void setArguments(JsonObject arguments) {
            JsonObject newArgs = new JsonObject(Maps.copyOfAllowingNullValues(Objects.requireNonNull(arguments).getMap()));
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
            this.response.set(Objects.requireNonNull(response));
        }

    }

    /**
     * Global input guardrails are executed before the guardrails declared on a tool. A guardrail declared on a tool that is
     * also a global guardrail is ignored.
     */
    private List<ToolInputGuardrail> allInputGuardrails(List<Class<? extends ToolInputGuardrail>> classes) {
        List<ToolInputGuardrail> declared = initGuardrails(classes, inputGuardrails, globalInputGuardrails, "input");
        if (globalInputGuardrails.isEmpty()) {
            return declared;
        }
        List<ToolInputGuardrail> ret = new ArrayList<>(globalInputGuardrails.instances);
        ret.addAll(declared);
        return ret;
    }

    /**
     * Global output guardrails are executed after the guardrails declared on a tool. A guardrail declared on a tool that is
     * also a global guardrail is ignored.
     */
    private List<ToolOutputGuardrail> allOutputGuardrails(List<Class<? extends ToolOutputGuardrail>> classes) {
        List<ToolOutputGuardrail> declared = initGuardrails(classes, outputGuardrails, globalOutputGuardrails, "output");
        if (globalOutputGuardrails.isEmpty()) {
            return declared;
        }
        List<ToolOutputGuardrail> ret = new ArrayList<>(declared);
        ret.addAll(globalOutputGuardrails.instances);
        return ret;
    }

    private static <G> List<G> initGuardrails(List<Class<? extends G>> classes, InjectableInstance<G> guardrails,
            GlobalGuardrails<G> global, String type) {
        if (classes == null || classes.isEmpty()) {
            return List.of();
        }
        List<G> ret = new ArrayList<>();
        for (Class<? extends G> clazz : classes) {
            InjectableInstance<? extends G> child = guardrails.select(clazz);
            if (child.isResolvable()) {
                InstanceHandle<? extends G> handle = child.getHandle();
                if (!global.beanIds.contains(handle.getBean().getIdentifier())) {
                    ret.add(handle.get());
                }
            } else {
                try {
                    ret.add(clazz.getConstructor().newInstance());
                } catch (Exception e) {
                    LOG.errorf(e, "Unable to instantiate %s guardrail: %s", type, clazz);
                }
            }
        }
        return ret;
    }

    /**
     * Global guardrails sorted by priority (higher priority goes first) and then by bean identifier.
     */
    static final class GlobalGuardrails<G> {

        final List<G> instances;
        final Set<String> beanIds;

        GlobalGuardrails(List<InstanceHandle<G>> handles) {
            List<InstanceHandle<G>> sorted = handles.stream()
                    .sorted(Comparator.<InstanceHandle<G>> comparingInt(h -> h.getBean().getPriority()).reversed()
                            .thenComparing(h -> h.getBean().getIdentifier()))
                    .toList();
            this.instances = sorted.stream().map(InstanceHandle::get).toList();
            this.beanIds = sorted.stream().map(h -> h.getBean().getIdentifier()).collect(Collectors.toUnmodifiableSet());
        }

        boolean isEmpty() {
            return instances.isEmpty();
        }

    }

    private static Uni<JsonObject> toolBeforeCall(List<ToolInputGuardrail> input, ToolInfo tool,
            FeatureExecutionContext context) {
        ToolInputContextImpl inputContext = new ToolInputContextImpl(tool, context.message(), context.mcpRequest());
        Iterator<ToolInputGuardrail> it = input.iterator();
        ToolInputGuardrail first = it.next();
        Uni<Void> uni;
        try {
            uni = first.applyAsync(inputContext);
        } catch (Exception e) {
            return Uni.createFrom().failure(e);
        }
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
        Uni<Void> uni;
        try {
            uni = first.applyAsync(outputContext);
        } catch (Exception e) {
            return Uni.createFrom().failure(e);
        }
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
            super(name, ToolManagerImpl.this.serverNames, ToolManagerImpl.this.validateServerNames);
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
        public ToolDefinition generateOutputSchema(Type from) {
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
            for (String serverName : serverNames) {
                McpServerBuildTimeConfig serverConfig = buildTimeConfig.servers().get(serverName);
                OptionalInt nameMaxLength = serverConfig.tools().nameMaxLength();
                if (nameMaxLength.isPresent() && name.length() > nameMaxLength.getAsInt()) {
                    throw new IllegalStateException("Tool name [%s] exceeds the maximum length of %s characters"
                            .formatted(name, nameMaxLength.getAsInt()));
                }
                Optional<Pattern> namePattern = serverConfig.tools().namePattern();
                if (namePattern.isPresent() && !namePattern.get().matcher(name).matches()) {
                    throw new IllegalStateException("Tool name [%s] does not match the pattern: %s"
                            .formatted(name, serverConfig.tools().namePattern()));
                }
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

            ToolDefinitionInfo ret = new ToolDefinitionInfo(name, title, description, serverNames, fun, asyncFun,
                    runOnVirtualThread, arguments, annotations, outputSchema, inputSchema, metadata,
                    allInputGuardrails(inputGuardrails), allOutputGuardrails(outputGuardrails), icons, transportHints,
                    notifyListChanged);
            List<FeatureKey> keys = FeatureKey.list(name, serverNames);
            registrationLock.lock();
            try {
                // Notify the ToolAdded observers before the tool is stored. A synchronous observer may veto the
                // registration by throwing (e.g. an invalid x-mcp-header); the exception then propagates without the
                // tool ever being added, keeping registration atomic.
                toolAddedEvent.fire(new ToolAdded(ret));
                for (FeatureKey key : keys) {
                    if (tools.containsKey(key)) {
                        throw toolAlreadyExists(name, key.serverName());
                    }
                }
                for (FeatureKey key : keys) {
                    tools.put(key, ret);
                }
                if (ret.notifyListChanged()) {
                    notifyConnections(McpMethod.NOTIFICATIONS_TOOLS_LIST_CHANGED, ret.serverNames());
                }
            } finally {
                registrationLock.unlock();
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
        private final Map<TransportHint, Object> transportHints;
        // Input schema generated by the global generator; null if set explicitly or if caching is disabled
        private final LazyValue<Object> generatedInputSchema;

        private ToolDefinitionInfo(String name, String title, String description, Set<String> serverNames,
                Function<ToolArguments, ToolResponse> fun,
                Function<ToolArguments, Uni<ToolResponse>> asyncFun, boolean runOnVirtualThread, List<ToolArgument> arguments,
                ToolAnnotations annotations,
                Object outputSchema, Object inputSchema, Map<MetaKey, Object> metadata, List<ToolInputGuardrail> input,
                List<ToolOutputGuardrail> output, List<Icon> icons, Map<TransportHint, Object> transportHints,
                boolean notifyListChanged) {
            super(name, description, serverNames, fun, asyncFun, runOnVirtualThread, icons, notifyListChanged);
            this.title = title;
            this.arguments = List.copyOf(arguments);
            this.annotations = Optional.ofNullable(annotations);
            this.outputSchema = outputSchema;
            this.inputSchema = inputSchema;
            this.metadata = Map.copyOf(metadata);
            this.input = input;
            this.output = output;
            this.transportHints = Map.copyOf(transportHints);
            this.generatedInputSchema = inputSchema == null && cacheSchemas(serverNames)
                    ? new LazyValue<>(this::generateInputSchema)
                    : null;
        }

        private Object generateInputSchema() {
            return globalInputSchemaGenerator.generate(this).value();
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
        public Map<TransportHint, Object> transportHints() {
            return transportHints;
        }

        @Override
        public Object inputSchema() {
            if (inputSchema != null) {
                return inputSchema;
            }
            return generatedInputSchema != null ? generatedInputSchema.get() : generateInputSchema();
        }

        @Override
        public Object outputSchema() {
            return outputSchema;
        }

        @Override
        public JsonObject asJson() {
            JsonObject tool = new JsonObject()
                    .put("name", name())
                    .put("description", description());
            if (title != null) {
                tool.put("title", title);
            }
            ToolAnnotations toolAnnotations = annotations.orElse(null);
            if (toolAnnotations != null) {
                tool.put("annotations", toolAnnotationsToJson(toolAnnotations));
            }
            tool.put("inputSchema", inputSchema());
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
            if (icons != null && !icons.isEmpty()) {
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
            this.args = Maps.copyOfAllowingNullValues(args);
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

    /**
     * Internal CDI event fired synchronously while a tool is being registered, before it is stored in the manager.
     * <p>
     * A synchronous observer of this event MAY veto the registration by throwing a runtime exception: the exception
     * propagates to the caller of {@link ToolDefinition#register()} and the tool is not registered. This is used, for
     * example, by the HTTP transport to reject a programmatically registered tool with an invalid {@code x-mcp-header}.
     */
    public record ToolAdded(ToolInfo tool) {
    }

    public record ToolRemoved(ToolInfo tool) {
    }

}
