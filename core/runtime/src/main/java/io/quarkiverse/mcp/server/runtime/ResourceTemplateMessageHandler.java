package io.quarkiverse.mcp.server.runtime;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.ResourceResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ResourceTemplateMessageHandler {

    private static final Logger LOG = Logger.getLogger(ResourceTemplateMessageHandler.class);

    private final ResourceTemplateManager manager;

    ResourceTemplateMessageHandler(ResourceTemplateManager manager) {
        this.manager = manager;
    }

    void resourceTemplatesList(JsonObject message, Responder responder) {
        Object id = message.getValue("id");
        LOG.debugf("List resource templates [id: %s]", id);
        JsonArray resources = new JsonArray();
        for (FeatureMetadata<ResourceResponse> resource : manager.list()) {
            resources.add(resource.asJson());
        }
        responder.sendResult(id, new JsonObject().put("resourceTemplates", resources));
    }

}
