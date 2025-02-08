package io.quarkiverse.mcp.server.runtime;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ResourceTemplateMessageHandler {

    private static final Logger LOG = Logger.getLogger(ResourceTemplateMessageHandler.class);

    private final ResourceTemplateManagerImpl manager;

    ResourceTemplateMessageHandler(ResourceTemplateManagerImpl manager) {
        this.manager = manager;
    }

    void resourceTemplatesList(JsonObject message, Responder responder) {
        Object id = message.getValue("id");
        LOG.debugf("List resource templates [id: %s]", id);
        JsonArray templates = new JsonArray();
        for (ResourceTemplateManager.ResourceTemplateInfo info : manager) {
            templates.add(info.asJson());
        }
        responder.sendResult(id, new JsonObject().put("resourceTemplates", templates));
    }

}
