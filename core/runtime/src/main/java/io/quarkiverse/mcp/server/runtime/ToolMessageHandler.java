package io.quarkiverse.mcp.server.runtime;

import java.lang.reflect.Type;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.ToolResponse;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ToolMessageHandler {

    private static final Logger LOG = Logger.getLogger(ToolMessageHandler.class);

    private final ToolManager toolManager;

    private final SchemaGenerator schemaGenerator;

    ToolMessageHandler(ToolManager toolManager) {
        this.toolManager = toolManager;
        this.schemaGenerator = new SchemaGenerator(
                new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON).build());
    }

    void toolsList(JsonObject message, Responder responder) {
        Object id = message.getValue("id");
        LOG.infof("List tools [id: %s]", id);

        JsonArray tools = new JsonArray();
        for (FeatureMetadata<ToolResponse> toolMetadata : toolManager.list()) {
            JsonObject tool = toolMetadata.asJson();
            JsonObject properties = new JsonObject();
            JsonArray required = new JsonArray();
            for (FeatureArgument a : toolMetadata.info().arguments()) {
                properties.put(a.name(), generateSchema(a.type(), a));
                if (a.required()) {
                    required.add(a.name());
                }
            }
            tool.put("inputSchema", new JsonObject()
                    .put("type", "object")
                    .put("properties", properties)
                    .put("required", required));
            tools.add(tool);
        }
        responder.sendResult(id, new JsonObject().put("tools", tools));
    }

    private Object generateSchema(Type type, FeatureArgument argument) {
        JsonNode jsonNode = schemaGenerator.generateSchema(type);
        if (jsonNode.isObject()) {
            ObjectNode objectNode = (ObjectNode) jsonNode;
            objectNode.remove("$schema");
            if (argument.description() != null && !argument.description().isBlank()) {
                objectNode.put("description", argument.description());
            }
        }
        return jsonNode;
    }

    void toolsCall(JsonObject message, Responder responder, McpConnection connection) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        String toolName = params.getString("name");
        LOG.infof("Call tool %s [id: %s]", toolName, id);

        ArgumentProviders argProviders = new ArgumentProviders(params.getJsonObject("arguments").getMap(), connection, id);

        try {
            Future<ToolResponse> fu = toolManager.execute(toolName, argProviders);
            fu.onComplete(new Handler<AsyncResult<ToolResponse>>() {
                @Override
                public void handle(AsyncResult<ToolResponse> ar) {
                    if (ar.succeeded()) {
                        ToolResponse toolResponse = ar.result();
                        responder.sendResult(id, toolResponse);
                    } else {
                        LOG.errorf(ar.cause(), "Unable to call tool %s", toolName);
                        responder.sendInternalError(id);
                    }
                }
            });
        } catch (McpException e) {
            responder.sendError(id, e.getJsonRpcError(), e.getMessage());
        }
    }

}
