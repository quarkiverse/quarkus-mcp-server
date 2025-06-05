package io.quarkiverse.mcp.server.runtime;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ResourceTemplateMessageHandler extends MessageHandler {

    private static final Logger LOG = Logger.getLogger(ResourceTemplateMessageHandler.class);

    private final ResourceTemplateManagerImpl manager;

    private final McpServersRuntimeConfig config;

    ResourceTemplateMessageHandler(ResourceTemplateManagerImpl manager, McpServersRuntimeConfig config) {
        this.manager = manager;
        this.config = config;
    }

    Future<Void> resourceTemplatesList(JsonObject message, McpRequest mcpRequest) {
        Object id = message.getValue("id");
        Cursor cursor = Messages.getCursor(message, mcpRequest.sender());
        if (cursor == null) {
            return Future.succeededFuture();
        }

        LOG.debugf("List resource templates [id: %s, cursor: %s]", id, cursor);

        McpServerRuntimeConfig serverConfig = config.servers().get(mcpRequest.serverName());
        if (serverConfig == null) {
            throw new IllegalStateException("Server config not found: " + mcpRequest.serverName());
        }
        int pageSize = serverConfig.resourceTemplates().pageSize();

        JsonArray templates = new JsonArray();
        JsonObject result = new JsonObject().put("resourceTemplates", templates);
        Page<ResourceTemplateManager.ResourceTemplateInfo> page = manager.fetchPage(mcpRequest, cursor, pageSize);
        for (ResourceTemplateManager.ResourceTemplateInfo info : page) {
            templates.add(info.asJson());
        }
        if (page.hasNextCursor()) {
            ResourceTemplateManager.ResourceTemplateInfo last = page.lastInfo();
            result.put("nextCursor", Cursor.encode(last.createdAt(), last.name()));
        }
        return mcpRequest.sender().sendResult(id, result);
    }

}
