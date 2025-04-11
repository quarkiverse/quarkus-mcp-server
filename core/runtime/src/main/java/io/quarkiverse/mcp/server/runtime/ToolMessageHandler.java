package io.quarkiverse.mcp.server.runtime;

import java.util.Map;
import java.util.Objects;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.runtime.FeatureManagerBase.FeatureExecutionContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ToolMessageHandler extends MessageHandler {

    private static final Logger LOG = Logger.getLogger(ToolMessageHandler.class);

    private final ToolManagerImpl manager;

    private final int pageSize;

    ToolMessageHandler(ToolManagerImpl manager, int pageSize) {
        this.manager = Objects.requireNonNull(manager);
        this.pageSize = pageSize;
    }

    void toolsList(JsonObject message, Sender sender) {
        Object id = message.getValue("id");
        Cursor cursor = Messages.getCursor(message, sender);

        LOG.debugf("List tools [id: %s, cursor: %s]", id, cursor);

        JsonArray tools = new JsonArray();
        JsonObject result = new JsonObject().put("tools", tools);
        Page<ToolManager.ToolInfo> page = manager.fetchPage(cursor, pageSize);
        for (ToolManager.ToolInfo info : page) {
            tools.add(info.asJson());
        }
        if (page.hasNextCursor()) {
            ToolManager.ToolInfo last = page.lastInfo();
            result.put("nextCursor", Cursor.encode(last.createdAt(), last.name()));
        }
        sender.sendResult(id, result);
    }

    void toolsCall(JsonObject message, Sender sender, McpConnection connection, SecuritySupport securitySupport) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        String toolName = params.getString("name");
        LOG.debugf("Call tool %s [id: %s]", toolName, id);

        Map<String, Object> args = params.containsKey("arguments") ? params.getJsonObject("arguments").getMap() : Map.of();
        ArgumentProviders argProviders = new ArgumentProviders(args, connection, id, null, sender,
                Messages.getProgressToken(message));

        try {
            Future<ToolResponse> fu = manager.execute(toolName, new FeatureExecutionContext(argProviders, securitySupport));
            fu.onComplete(new Handler<AsyncResult<ToolResponse>>() {
                @Override
                public void handle(AsyncResult<ToolResponse> ar) {
                    if (ar.succeeded()) {
                        ToolResponse toolResponse = ar.result();
                        sender.sendResult(id, toolResponse);
                    } else {
                        Throwable cause = ar.cause();
                        if (cause instanceof ToolCallException tce) {
                            // Business logic error should result in ToolResponse with isError:true
                            sender.sendResult(id, ToolResponse.error(tce.getMessage()));
                        } else {
                            handleFailure(id, sender, connection, cause, LOG, "Unable to call tool %s", toolName);
                        }
                    }
                }
            });
        } catch (McpException e) {
            sender.sendError(id, e.getJsonRpcError(), e.getMessage());
        }
    }

}
