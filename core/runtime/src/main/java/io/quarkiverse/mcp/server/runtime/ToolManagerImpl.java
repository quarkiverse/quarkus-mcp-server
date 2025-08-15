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
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;

import io.quarkiverse.mcp.server.DefaultValueConverter;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpLog;
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

    private final SchemaGenerator schemaGenerator;

    final ConcurrentMap<String, ToolInfo> tools;

    final Map<Type, DefaultValueConverter<?>> defaultValueConverters;

    final List<ToolFilter> filters;

    ToolManagerImpl(McpMetadata metadata,
            Vertx vertx,
            ObjectMapper mapper,
            ConnectionManager connectionManager,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation,
            ResponseHandlers responseHandlers,
            @All List<ToolFilter> filters,
            @All List<SchemaGeneratorConfigCustomizer> schemaGeneratorConfigCustomizers) {
        super(vertx, mapper, connectionManager, currentIdentityAssociation, responseHandlers);
        this.tools = new ConcurrentHashMap<>();
        for (FeatureMetadata<ToolResponse> f : metadata.tools()) {
            this.tools.put(f.info().name(), new ToolMethod(f));
        }
        this.schemaGenerator = constructSchemaGenerator(schemaGeneratorConfigCustomizers);
        this.defaultValueConverters = metadata.defaultValueConverters();
        this.filters = filters;
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
        return new McpException("Invalid tool name: " + id, JsonRPC.INVALID_PARAMS);
    }

    @Override
    protected Map<Type, DefaultValueConverter<?>> defaultValueConverters() {
        return defaultValueConverters;
    }

    Object generateSchema(Type type, String description, String defaultValue) {
        JsonNode jsonNode = schemaGenerator.generateSchema(type);
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
            return objectNode;
        }
        return jsonNode;
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
            JsonObject tool = metadata.asJson();
            JsonObject properties = new JsonObject();
            JsonArray required = new JsonArray();
            for (FeatureArgument a : metadata.info().serializedArguments()) {
                properties.put(a.name(), generateSchema(a.type(), a.description(), a.defaultValue()));
                if (a.required()) {
                    required.add(a.name());
                }
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
            return tool;
        }

    }

    private static SchemaGenerator constructSchemaGenerator(
            List<SchemaGeneratorConfigCustomizer> schemaGeneratorConfigCustomizers) {
        var configBuilder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .without(Option.SCHEMA_VERSION_INDICATOR);
        for (SchemaGeneratorConfigCustomizer customizer : schemaGeneratorConfigCustomizers) {
            customizer.customize(configBuilder);
        }
        return new SchemaGenerator(configBuilder.build());
    }

    class ToolDefinitionImpl
            extends FeatureManagerBase.FeatureDefinitionBase<ToolInfo, ToolArguments, ToolResponse, ToolDefinitionImpl>
            implements ToolManager.ToolDefinition {

        private String title;
        private final List<ToolArgument> arguments;

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
        public ToolInfo register() {
            validate();
            ToolDefinitionInfo ret = new ToolDefinitionInfo(name, title, description, serverName, fun, asyncFun,
                    runOnVirtualThread, arguments, annotations);
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

        private ToolDefinitionInfo(String name, String title, String description, String serverName,
                Function<ToolArguments, ToolResponse> fun,
                Function<ToolArguments, Uni<ToolResponse>> asyncFun, boolean runOnVirtualThread, List<ToolArgument> arguments,
                ToolAnnotations annotations) {
            super(name, description, serverName, fun, asyncFun, runOnVirtualThread);
            this.title = title;
            this.arguments = List.copyOf(arguments);
            this.annotations = Optional.ofNullable(annotations);
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
