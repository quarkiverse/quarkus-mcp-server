package io.quarkiverse.mcp.server.runtime;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;

import io.quarkiverse.mcp.server.RequestId;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@Singleton
public class ToolManagerImpl extends FeatureManagerBase<ToolResponse, ToolInfo> implements ToolManager {

    private final SchemaGenerator schemaGenerator;

    final ConcurrentMap<String, ToolInfo> tools;

    ToolManagerImpl(McpMetadata metadata, Vertx vertx, ObjectMapper mapper, ConnectionManager connectionManager,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation) {
        super(vertx, mapper, connectionManager, currentIdentityAssociation);
        this.tools = new ConcurrentHashMap<>();
        for (FeatureMetadata<ToolResponse> f : metadata.tools()) {
            this.tools.put(f.info().name(), new ToolMethod(f));
        }
        this.schemaGenerator = new SchemaGenerator(
                new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON).build());
    }

    @Override
    Stream<ToolInfo> infoStream() {
        return tools.values().stream();
    }

    @Override
    public int size() {
        return tools.size();
    }

    @Override
    public ToolInfo getTool(String name) {
        return tools.get(name);
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
        return tools.computeIfPresent(name, (key, value) -> {
            if (!value.isMethod()) {
                notifyConnections("notifications/tools/list_changed");
                return null;
            }
            return value;
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    protected FeatureInvoker<ToolResponse> getInvoker(String id) {
        ToolInfo tool = tools.get(id);
        if (tool instanceof FeatureInvoker fi) {
            return fi;
        }
        return null;
    }

    @Override
    protected McpException notFound(String id) {
        return new McpException("Invalid tool name: " + id, JsonRPC.INVALID_PARAMS);
    }

    Object generateSchema(Type type, String description) {
        JsonNode jsonNode = schemaGenerator.generateSchema(type);
        if (jsonNode.isObject()) {
            ObjectNode objectNode = (ObjectNode) jsonNode;
            objectNode.remove("$schema");
            if (description != null && !description.isBlank()) {
                objectNode.put("description", description);
            }
            if (Types.isOptional(type)) {
                ObjectNode valueType = objectNode.withObjectProperty("properties").withObjectProperty("value");
                objectNode.set("type", valueType.get("type"));
                if (valueType.has("properties")) {
                    objectNode.set("properties", valueType.get("properties"));
                } else {
                    objectNode.remove("properties");
                }
            }
        }
        return jsonNode;
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
        public String description() {
            return metadata.info().description();
        }

        @Override
        public boolean isMethod() {
            return true;
        }

        @Override
        public List<ToolArgument> arguments() {
            return metadata.info().serializedArguments().stream()
                    .map(fa -> new ToolArgument(fa.name(), fa.description(), fa.required(), fa.type())).toList();
        }

        @Override
        public JsonObject asJson() {
            JsonObject tool = metadata.asJson();
            JsonObject properties = new JsonObject();
            JsonArray required = new JsonArray();
            for (FeatureArgument a : metadata.info().serializedArguments()) {
                properties.put(a.name(), generateSchema(a.type(), a.description()));
                if (a.required()) {
                    required.add(a.name());
                }
            }
            tool.put("inputSchema", new JsonObject()
                    .put("type", "object")
                    .put("properties", properties)
                    .put("required", required));
            return tool;
        }

    }

    class ToolDefinitionImpl
            extends FeatureManagerBase.FeatureDefinitionBase<ToolInfo, ToolArguments, ToolResponse, ToolDefinitionImpl>
            implements ToolManager.ToolDefinition {

        private final List<ToolArgument> arguments;

        private ToolDefinitionImpl(String name) {
            super(name);
            this.arguments = new ArrayList<>();
        }

        @Override
        public ToolDefinition addArgument(String name, String description, boolean required, Type type) {
            arguments.add(new ToolArgument(name, description, required, type));
            return this;
        }

        @Override
        public ToolInfo register() {
            validate();
            ToolDefinitionInfo ret = new ToolDefinitionInfo(name, description, fun, asyncFun,
                    runOnVirtualThread, arguments);
            ToolInfo existing = tools.putIfAbsent(name, ret);
            if (existing != null) {
                throw toolAlreadyExists(name);
            } else {
                notifyConnections("notifications/tools/list_changed");
            }
            return ret;
        }
    }

    class ToolDefinitionInfo extends FeatureManagerBase.FeatureDefinitionInfoBase<ToolArguments, ToolResponse>
            implements ToolManager.ToolInfo {

        private final List<ToolArgument> arguments;

        private ToolDefinitionInfo(String name, String description, Function<ToolArguments, ToolResponse> fun,
                Function<ToolArguments, Uni<ToolResponse>> asyncFun, boolean runOnVirtualThread, List<ToolArgument> arguments) {
            super(name, description, fun, asyncFun, runOnVirtualThread);
            this.arguments = List.copyOf(arguments);
        }

        @Override
        public List<ToolArgument> arguments() {
            return arguments;
        }

        @Override
        public JsonObject asJson() {
            JsonObject tool = new JsonObject()
                    .put("name", name())
                    .put("description", description());
            JsonObject properties = new JsonObject();
            JsonArray required = new JsonArray();
            for (ToolArgument a : arguments) {
                properties.put(a.name(), generateSchema(a.type(), a.description()));
                if (a.required()) {
                    required.add(a.name());
                }
            }
            tool.put("inputSchema", new JsonObject()
                    .put("type", "object")
                    .put("properties", properties)
                    .put("required", required));
            return tool;
        }

        @Override
        protected ToolArguments createArguments(ArgumentProviders argumentProviders) {
            return new ToolArguments(argumentProviders.args(),
                    argumentProviders.connection(),
                    log(Feature.TOOL.toString().toLowerCase() + ":" + name, name, argumentProviders),
                    new RequestId(argumentProviders.requestId()));
        }

    }

}
