package io.quarkiverse.mcp.server.runtime;

import java.lang.reflect.Type;
import java.util.ArrayList;
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

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.DefaultValueConverter;
import io.quarkiverse.mcp.server.GlobalInputSchemaGenerator;
import io.quarkiverse.mcp.server.GlobalOutputSchemaGenerator;
import io.quarkiverse.mcp.server.InputSchemaGenerator;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.OutputSchemaGenerator;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolFilter;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
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

    ToolManagerImpl(McpMetadata metadata,
            Vertx vertx,
            ObjectMapper mapper,
            ConnectionManager connectionManager,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation,
            ResponseHandlers responseHandlers,
            @All List<ToolFilter> filters,
            GlobalInputSchemaGenerator globalInputSchemaGenerator,
            GlobalOutputSchemaGenerator globalOutputSchemaGenerator,
            Instance<InputSchemaGenerator<?>> inputSchemaGenerator,
            Instance<OutputSchemaGenerator> outputSchemaGenerator,
            McpServersBuildTimeConfig buildTimeConfig,
            McpServersRuntimeConfig config) {
        super(vertx, mapper, connectionManager, currentIdentityAssociation, responseHandlers);
        this.tools = new ConcurrentHashMap<>();
        for (FeatureMetadata<ToolResponse> f : metadata.tools()) {
            this.tools.put(f.info().name(), new ToolMethod(f));
        }
        this.globalInputSchemaGenerator = globalInputSchemaGenerator;
        this.globalOutputSchemaGenerator = globalOutputSchemaGenerator;
        this.outputSchemaGenerator = outputSchemaGenerator;
        this.inputSchemaGenerator = inputSchemaGenerator;
        this.defaultValueConverters = metadata.defaultValueConverters();
        this.filters = filters;
        this.buildTimeConfig = buildTimeConfig;
        this.config = config;
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

        private ToolMethod(FeatureMetadata<ToolResponse> metadata) {
            super(metadata);
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
            return tool;
        }

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
        public ToolInfo register() {
            validate();
            OptionalInt nameMaxLength = buildTimeConfig.servers().get(serverName).tools().nameMaxLength();
            if (nameMaxLength.isPresent() && name.length() > nameMaxLength.getAsInt()) {
                throw new IllegalStateException("Tool name [%s] exceeds the maximum length of %s characters"
                        .formatted(name, nameMaxLength.getAsInt()));
            }
            ToolDefinitionInfo ret = new ToolDefinitionInfo(name, title, description, serverName, fun, asyncFun,
                    runOnVirtualThread, arguments, annotations, outputSchema, inputSchema, metadata);
            ToolInfo existing = tools.putIfAbsent(name, ret);
            if (existing != null) {
                throw toolAlreadyExists(name);
            } else {
                notifyConnections(McpMessageHandler.NOTIFICATIONS_TOOLS_LIST_CHANGED);
            }
            return ret;
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

        private ToolDefinitionInfo(String name, String title, String description, String serverName,
                Function<ToolArguments, ToolResponse> fun,
                Function<ToolArguments, Uni<ToolResponse>> asyncFun, boolean runOnVirtualThread, List<ToolArgument> arguments,
                ToolAnnotations annotations,
                Object outputSchema, Object inputSchema, Map<MetaKey, Object> metadata) {
            super(name, description, serverName, fun, asyncFun, runOnVirtualThread);
            this.title = title;
            this.arguments = List.copyOf(arguments);
            this.annotations = Optional.ofNullable(annotations);
            this.outputSchema = outputSchema;
            this.inputSchema = inputSchema;
            this.metadata = Map.copyOf(metadata);
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

}
