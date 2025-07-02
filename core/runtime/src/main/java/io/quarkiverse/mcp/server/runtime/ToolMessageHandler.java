package io.quarkiverse.mcp.server.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.runtime.FeatureManagerBase.FeatureExecutionContext;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ToolMessageHandler extends MessageHandler {

    private static final Logger LOG = Logger.getLogger(ToolMessageHandler.class);

    private final ToolManagerImpl manager;

    private final McpServersRuntimeConfig config;

    ToolMessageHandler(ToolManagerImpl manager, McpServersRuntimeConfig config) {
        this.manager = Objects.requireNonNull(manager);
        this.config = config;
    }

    Future<Void> toolsList(JsonObject message, McpRequest mcpRequest) {
        Object id = message.getValue("id");
        Cursor cursor = Messages.getCursor(message, mcpRequest.sender());
        if (cursor == null) {
            return Future.succeededFuture();
        }

        LOG.debugf("List tools [id: %s, cursor: %s]", id, cursor);

        McpServerRuntimeConfig serverConfig = config.servers().get(mcpRequest.serverName());
        if (serverConfig == null) {
            throw new IllegalStateException("Server config not found: " + mcpRequest.serverName());
        }
        int pageSize = serverConfig.tools().pageSize();

        JsonArray tools = new JsonArray();
        JsonObject result = new JsonObject().put("tools", tools);
        Page<ToolManager.ToolInfo> page = manager.fetchPage(mcpRequest, cursor, pageSize);
        for (ToolManager.ToolInfo info : page) {
            tools.add(info.asJson());
        }
        if (page.hasNextCursor()) {
            ToolManager.ToolInfo last = page.lastInfo();
            result.put("nextCursor", Cursor.encode(last.createdAt(), last.name()));
        }
        return mcpRequest.sender().sendResult(id, result);
    }

    Future<Void> toolsCall(JsonObject message, McpRequest mcpRequest) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        String toolName = params.getString("name");
        LOG.debugf("Call tool %s [id: %s]", toolName, id);

        Map<String, Object> args = params.containsKey("arguments") ? params.getJsonObject("arguments").getMap()
                : new HashMap<>();
        ArgumentProviders argProviders = new ArgumentProviders(args, mcpRequest.connection(), id, null, mcpRequest.sender(),
                Messages.getProgressToken(message), manager.responseHandlers, mcpRequest.serverName());

        try {
            Future<ToolResponse> fu = manager.execute(toolName,
                    new FeatureExecutionContext(argProviders, mcpRequest));
            return fu.compose(toolResponse -> mcpRequest.sender().sendResult(id, toolResponse), cause -> {
                if (cause instanceof ToolCallException tce) {
                    // Business logic error should result in ToolResponse with isError:true
                    return mcpRequest.sender().sendResult(id, ToolResponse.error(tce.getMessage()));
                } else {
                    return handleFailure(id, mcpRequest.sender(), mcpRequest.connection(), cause, LOG,
                            "Unable to call tool %s", toolName);
                }
            });
        } catch (McpException e) {
            return mcpRequest.sender().sendError(id, e.getJsonRpcError(), e.getMessage());
        }
    }

}
