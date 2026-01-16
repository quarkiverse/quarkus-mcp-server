package io.quarkiverse.mcp.server.runtime;

import static io.quarkiverse.mcp.server.runtime.Messages.getParams;

import java.util.Objects;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpException;
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
            try {
                tools.add(info.asJson());
            } catch (McpException e) {
                return mcpRequest.sender().sendError(id, e.getJsonRpcErrorCode(), e.getMessage());
            } catch (Exception e) {
                LOG.errorf(e, "Unable to encode Tool [%s] as JSON", info.name());
                return mcpRequest.sender().sendInternalError(id);
            }
        }
        if (page.hasNextCursor()) {
            ToolManager.ToolInfo last = page.lastInfo();
            result.put("nextCursor", Cursor.encode(last.createdAt(), last.name()));
        }
        return mcpRequest.sender().sendResult(id, result);
    }

    Future<Void> toolsCall(JsonObject message, McpRequest mcpRequest) {
        Object id = message.getValue("id");
        JsonObject params = getParams(message);
        String toolName = params.getString("name");
        LOG.debugf("Call tool %s [id: %s]", toolName, id);
        try {
            Future<ToolResponse> fu = manager.execute(toolName,
                    new FeatureExecutionContext(message, mcpRequest));
            return fu.compose(toolResponse -> mcpRequest.sender().sendResult(id, toolResponse),
                    cause -> handleFailure(id, mcpRequest.sender(), mcpRequest.connection(), cause, LOG,
                            "Unable to call tool %s", toolName));
        } catch (McpException e) {
            return mcpRequest.sender().sendError(id, e.getJsonRpcErrorCode(), e.getMessage());
        }
    }

}
