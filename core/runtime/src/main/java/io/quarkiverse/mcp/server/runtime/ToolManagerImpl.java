package io.quarkiverse.mcp.server.runtime;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.mcp.server.DefaultValueConverter;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.OutputSchemaGenerator;
import io.quarkiverse.mcp.server.ToolFilter;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.arc.All;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@Singleton
public class ToolManagerImpl extends FeatureManagerBase<ToolResponse, ToolInfo> implements ToolManager {

    private static final Logger LOG = Logger.getLogger(ToolManagerImpl.class);

    private final DefaultSchemaGenerator schemaGenerator;
    private final Map<String, Class<?>> toolArgumentHolders;

    final ConcurrentMap<String, ToolInfo> tools;

    final Map<Type, DefaultValueConverter<?>> defaultValueConverters;

    final List<ToolFilter> filters;

    final Instance<OutputSchemaGenerator> outputSchemaGenerator;

    ToolManagerImpl(McpMetadata metadata,
            Vertx vertx,
            ObjectMapper mapper,
            ConnectionManager connectionManager,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation,
            ResponseHandlers responseHandlers,
            @All List<ToolFilter> filters,
            DefaultSchemaGenerator schemaGenerator,
            Instance<OutputSchemaGenerator> outputSchemaGenerator) {
        super(vertx, mapper, connectionManager, currentIdentityAssociation, responseHandlers);
        this.tools = new ConcurrentHashMap<>();
        for (FeatureMetadata<ToolResponse> f : metadata.tools()) {
            this.tools.put(f.info().name(), new ToolMethod(f));
        }
        this.schemaGenerator = schemaGenerator;
        this.defaultValueConverters = metadata.defaultValueConverters();
        this.outputSchemaGenerator = outputSchemaGenerator;
        this.filters = filters;
        this.toolArgumentHolders = metadata.toolArgumentHolders();
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

    Object generateSchema(Class<?> holderClass, List<FeatureArgument> serializedArguments) {
        JsonNode jsonNode = schemaGenerator.generateSchema(holderClass);
        if (jsonNode.isObject()) {
            ObjectNode objectNode = (ObjectNode) jsonNode;
            for (FeatureArgument arg : serializedArguments) {
                JsonNode node = objectNode.get(arg.name());
                if (node != null) {
                    postProcessJsonNode(node, arg.type(), arg.description(), arg.defaultValue());
                }
            }
            return objectNode.get("properties");
        }
        return jsonNode;
    }

    Object generateSchema(Type type, String description, String defaultValue) {
        JsonNode jsonNode = schemaGenerator.generateSchema(type);
        postProcessJsonNode(jsonNode, type, description, defaultValue);
        return jsonNode;
    }

    private void postProcessJsonNode(JsonNode jsonNode, Type type, String description, String defaultValue) {
        if (jsonNode.isObject()) {
            ObjectNode objectNode = (ObjectNode) jsonNode;
            if (Types.isOptional(type)) {
                // The generated schema for Optional<List<String>> looks like:
                // {"type":"object","properties":{"value":{"type":"array","items":{"type":"string"}}}}
                // We need to extract the value property and replace the original object node
                ObjectNode valueProp = objectNode.withObjectProperty("properties").withObjectProperty("value");
                if (valueProp != null) {
                    objectNode = valueProp;
                }
            }
            if (description != null && !description.isBlank()) {
                objectNode.put("description", description);
            }
            if (defaultValue != null) {
                Object converted = convert(defaultValue, type);
                objectNode.putPOJO("default", converted);
            }
        }
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
            Object properties;
            JsonArray required = new JsonArray();
            for (FeatureArgument a : metadata.info().serializedArguments()) {
                if (a.required()) {
                    required.add(a.name());
                }
            }

            Class<?> holder = toolArgumentHolders.get(name());
            if (holder != null) {
                properties = generateSchema(holder, metadata.info().serializedArguments());
            } else {
                JsonObject props = new JsonObject();
                for (FeatureArgument a : metadata.info().serializedArguments()) {
                    props.put(a.name(), generateSchema(a.type(), a.description(), a.defaultValue()));
                }
                properties = props;
            }

            ToolAnnotations toolAnnotations = metadata.info().toolAnnotations();
            if (toolAnnotations != null) {
                tool.put("annotations", new JsonObject()
                        .put("title", toolAnnotations.title())
                        .put("destructiveHint", toolAnnotations.destructiveHint())
                        .put("idempotentHint", toolAnnotations.idempotentHint())
                        .put("openWorldHint", toolAnnotations.openWorldHint())
                        .put("readOnlyHint", toolAnnotations.readOnlyHint()));
            }
            tool.put("inputSchema", new JsonObject()
                    .put("type", "object")
                    .put("properties", properties)
                    .put("required", required));
            Class<? extends OutputSchemaGenerator> outputSchemaGeneratorClass = metadata.info().outputSchemaGenerator();
            if (outputSchemaGeneratorClass != null) {
                Object outputSchema;
                if (DefaultSchemaGenerator.class.equals(outputSchemaGeneratorClass)) {
                    outputSchema = schemaGenerator.generate(metadata.info().outputSchemaFrom());
                } else {
                    outputSchema = outputSchemaGenerator.select(outputSchemaGeneratorClass).get()
                            .generate(metadata.info().outputSchemaFrom());
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
            this.outputSchema = schemaGenerator.generate(from);
            return this;
        }

        @Override
        public ToolDefinition setOutputSchema(Object schema) {
            this.outputSchema = schema;
            return this;
        }

        @Override
        public ToolInfo register() {
            validate();
            ToolDefinitionInfo ret = new ToolDefinitionInfo(name, title, description, serverName, fun, asyncFun,
                    runOnVirtualThread, arguments, annotations, outputSchema);
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

        private ToolDefinitionInfo(String name, String title, String description, String serverName,
                Function<ToolArguments, ToolResponse> fun,
                Function<ToolArguments, Uni<ToolResponse>> asyncFun, boolean runOnVirtualThread, List<ToolArgument> arguments,
                ToolAnnotations annotations,
                Object outputSchema) {
            super(name, description, serverName, fun, asyncFun, runOnVirtualThread);
            this.title = title;
            this.arguments = List.copyOf(arguments);
            this.annotations = Optional.ofNullable(annotations);
            this.outputSchema = outputSchema;
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
        public JsonObject asJson() {
            JsonObject tool = new JsonObject()
                    .put("name", name())
                    .put("description", description());
            if (title != null) {
                tool.put("title", title);
            }
            JsonObject properties = new JsonObject();
            JsonArray required = new JsonArray();
            for (ToolArgument a : arguments) {
                properties.put(a.name(), generateSchema(a.type(), a.description(), a.defaultValue()));
                if (a.required()) {
                    required.add(a.name());
                }
            }
            if (annotations.isPresent()) {
                tool.put("annotations", new JsonObject()
                        .put("title", annotations.get().title())
                        .put("destructiveHint", annotations.get().destructiveHint())
                        .put("idempotentHint", annotations.get().idempotentHint())
                        .put("openWorldHint", annotations.get().openWorldHint())
                        .put("readOnlyHint", annotations.get().readOnlyHint()));
            }
            tool.put("inputSchema", new JsonObject()
                    .put("type", "object")
                    .put("properties", properties)
                    .put("required", required));
            if (outputSchema != null) {
                tool.put("outputSchema", outputSchema);
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
