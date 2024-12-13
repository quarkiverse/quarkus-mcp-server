package io.quarkiverse.mcp.server.runtime;

import java.lang.reflect.Type;
import java.util.List;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;

import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.arc.Arc;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class ToolMessageHandler {

    private static final Logger LOG = Logger.getLogger(ToolMessageHandler.class);

    private final SchemaGenerator schemaGenerator;

    ToolMessageHandler() {
        this.schemaGenerator = new SchemaGenerator(
                new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON).build());
    }

    void toolsList(JsonObject message, RoutingContext ctx) {
        Object id = message.getValue("id");
        LOG.infof("List tools [id: %s]", id);
        ToolManager toolManager = Arc.container().instance(ToolManager.class).get();
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        List<FeatureMethodInfo> tools = toolManager.list();
        JsonArray toolsArray = new JsonArray();
        for (FeatureMethodInfo info : tools) {
            JsonObject tool = new JsonObject()
                    .put("name", info.name())
                    .put("description", info.description());
            JsonObject properties = new JsonObject();
            JsonArray required = new JsonArray();
            for (FeatureArgument a : info.arguments()) {
                properties.put(a.name(), generateSchema(a.type(), a));
                if (a.required()) {
                    required.add(a.name());
                }
            }
            tool.put("inputSchema", new JsonObject()
                    .put("type", "object")
                    .put("properties", properties)
                    .put("required", required));
            toolsArray.add(tool);
        }

        ctx.end(McpMessagesHandler.newResult(id, new JsonObject()
                .put("tools", toolsArray)).encode());
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

    void toolsCall(JsonObject message, RoutingContext ctx, McpConnectionImpl connection) {
        Object id = message.getValue("id");
        LOG.infof("Call tool [id: %s]", id);

        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        JsonObject params = message.getJsonObject("params");
        ArgumentProviders argProviders = new ArgumentProviders(params.getJsonObject("arguments").getMap(), connection, id);

        ToolManager toolManager = Arc.container().instance(ToolManager.class).get();
        Future<ToolResponse> fu = toolManager.get(params.getString("name"), argProviders);
        fu.onComplete(new Handler<AsyncResult<ToolResponse>>() {
            @Override
            public void handle(AsyncResult<ToolResponse> ar) {
                if (ar.succeeded()) {
                    ToolResponse toolResponse = ar.result();
                    ctx.end(McpMessagesHandler.newResult(id, toolResponse).encode());
                } else {
                    LOG.error("Unable to obtain prompt", ar.cause());
                    ctx.fail(500);
                }
            }
        });
    }

}
