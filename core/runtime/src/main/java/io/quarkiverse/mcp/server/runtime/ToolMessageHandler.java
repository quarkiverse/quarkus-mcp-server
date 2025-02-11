package io.quarkiverse.mcp.server.runtime;

import java.util.Map;
import java.util.Objects;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ToolMessageHandler {

    private static final Logger LOG = Logger.getLogger(ToolMessageHandler.class);

    private final ToolManagerImpl manager;

    ToolMessageHandler(ToolManagerImpl manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    void toolsList(JsonObject message, Responder responder) {
        Object id = message.getValue("id");
        LOG.debugf("List tools [id: %s]", id);

        JsonArray tools = new JsonArray();
        for (ToolManager.ToolInfo tool : manager) {
            tools.add(tool.asJson());
        }
        responder.sendResult(id, new JsonObject().put("tools", tools));
    }

    void toolsCall(JsonObject message, Responder responder, McpConnection connection) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        String toolName = params.getString("name");
        LOG.debugf("Call tool %s [id: %s]", toolName, id);

        Map<String, Object> args = params.containsKey("arguments") ? params.getJsonObject("arguments").getMap() : Map.of();
        ArgumentProviders argProviders = new ArgumentProviders(args, connection, id, null, responder);

        try {
            Future<ToolResponse> fu = manager.execute(toolName, argProviders);
            fu.onComplete(new Handler<AsyncResult<ToolResponse>>() {
                @Override
                public void handle(AsyncResult<ToolResponse> ar) {
                    if (ar.succeeded()) {
                        ToolResponse toolResponse = ar.result();
                        responder.sendResult(id, toolResponse);
                    } else {
                        Throwable cause = ar.cause();
                        if (cause instanceof ToolCallException tce) {
                            // Business logic error should result in ToolResponse with isError:true
                            responder.sendResult(id, ToolResponse.error(tce.getMessage()));
                        } else if (cause instanceof McpException mcp) {
                            responder.sendError(id, mcp.getJsonRpcError(), mcp.getMessage());
                        } else {
                            LOG.errorf(ar.cause(), "Unable to call tool %s", toolName);
                            responder.sendInternalError(id);
                        }
                    }
                }
            });
        } catch (McpException e) {
            responder.sendError(id, e.getJsonRpcError(), e.getMessage());
        }
    }

}
