package io.quarkiverse.mcp.server.runtime;

import java.util.Objects;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.runtime.FeatureManagerBase.FeatureExecutionContext;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class PromptMessageHandler extends MessageHandler {

    private static final Logger LOG = Logger.getLogger(PromptMessageHandler.class);

    private final PromptManagerImpl manager;

    private final McpServersRuntimeConfig config;

    PromptMessageHandler(PromptManagerImpl manager, McpServersRuntimeConfig config) {
        this.manager = Objects.requireNonNull(manager);
        this.config = config;
    }

    Future<Void> promptsList(JsonObject message, McpRequest mcpRequest) {
        Object id = message.getValue("id");
        Cursor cursor = Messages.getCursor(message, mcpRequest.sender());
        if (cursor == null) {
            return Future.succeededFuture();
        }

        LOG.debugf("List prompts [id: %s, cursor: %s]", id, cursor);

        McpServerRuntimeConfig serverConfig = config.servers().get(mcpRequest.serverName());
        if (serverConfig == null) {
            throw new IllegalStateException("Server config not found: " + mcpRequest.serverName());
        }
        int pageSize = serverConfig.prompts().pageSize();

        JsonArray prompts = new JsonArray();
        JsonObject result = new JsonObject().put("prompts", prompts);
        Page<PromptManager.PromptInfo> page = manager.fetchPage(mcpRequest, cursor, pageSize);
        for (PromptManager.PromptInfo info : page) {
            prompts.add(info.asJson());
        }
        if (page.hasNextCursor()) {
            PromptManager.PromptInfo last = page.lastInfo();
            result.put("nextCursor", Cursor.encode(last.createdAt(), last.name()));
        }
        return mcpRequest.sender().sendResult(id, result);
    }

    Future<Void> promptsGet(JsonObject message, McpRequest mcpRequest) {
        Object id = message.getValue("id");
        JsonObject params = Messages.getParams(message);
        String promptName = params.getString("name");
        LOG.debugf("Get prompt %s [id: %s]", promptName, id);
        try {
            Future<PromptResponse> fu = manager.execute(promptName,
                    new FeatureExecutionContext(message, mcpRequest));
            return fu.compose(promptResponse -> {
                JsonObject result = new JsonObject();
                if (promptResponse.description() != null) {
                    result.put("description", promptResponse.description());
                }
                result.put("messages", promptResponse.messages());
                return mcpRequest.sender().sendResult(id, result);
            }, cause -> handleFailure(id, mcpRequest.sender(), mcpRequest.connection(), cause, LOG,
                    "Unable to obtain prompt %s", promptName));
        } catch (McpException e) {
            return mcpRequest.sender().sendError(id, e.getJsonRpcErrorCode(), e.getMessage());
        }

    }

}
