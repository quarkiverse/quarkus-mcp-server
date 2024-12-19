package io.quarkiverse.mcp.server.runtime;

import static io.quarkiverse.mcp.server.runtime.Messages.internalError;
import static io.quarkiverse.mcp.server.runtime.Messages.newError;

import java.util.Map;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.runtime.McpMessagesHandler.Responder;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ResourceMessageHandler {

    private static final Logger LOG = Logger.getLogger(ResourceMessageHandler.class);

    private final ResourceManager resourceManager;

    ResourceMessageHandler(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    void resourcesList(JsonObject message, Responder responder) {
        Object id = message.getValue("id");
        LOG.infof("List resources [id: %s]", id);
        JsonArray resources = new JsonArray();
        for (FeatureMetadata<ResourceResponse> resource : resourceManager.list()) {
            resources.add(resource.asJson());
        }
        responder.ok(Messages.newResult(id, new JsonObject()
                .put("resources", resources)));
    }

    void resourcesRead(JsonObject message, Responder responder, McpConnectionImpl connection) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        String resourceUri = params.getString("uri");
        if (resourceUri == null) {
            responder.badRequest("Resource URI not defined");
            return;
        }
        LOG.infof("Read resource %s [id: %s]", resourceUri, id);

        ArgumentProviders argProviders = new ArgumentProviders(Map.of("uri", resourceUri), connection, id);

        try {
            Future<ResourceResponse> fu = resourceManager.get(resourceUri, argProviders);
            fu.onComplete(new Handler<AsyncResult<ResourceResponse>>() {
                @Override
                public void handle(AsyncResult<ResourceResponse> ar) {
                    if (ar.succeeded()) {
                        ResourceResponse resourceResponse = ar.result();
                        responder.ok(Messages.newResult(id, resourceResponse));
                    } else {
                        LOG.errorf(ar.cause(), "Unable to read resource %s", resourceUri);
                        responder.ok(internalError(id));
                    }
                }
            });
        } catch (McpException e) {
            responder.ok(newError(id, e.getJsonRpcError(), e.getMessage()));
        }
    }

}
