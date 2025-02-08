package io.quarkiverse.mcp.server.runtime;

import java.util.Map;
import java.util.Objects;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.ResourceManager.ResourceInfo;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ResourceMessageHandler {

    private static final Logger LOG = Logger.getLogger(ResourceMessageHandler.class);

    private final ResourceManagerImpl manager;

    ResourceMessageHandler(ResourceManagerImpl manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    void resourcesList(JsonObject message, Responder responder) {
        Object id = message.getValue("id");
        LOG.debugf("List resources [id: %s]", id);
        JsonArray resources = new JsonArray();
        for (ResourceInfo info : manager) {
            resources.add(info.asJson());
        }
        responder.sendResult(id, new JsonObject().put("resources", resources));
    }

    void resourcesRead(JsonObject message, Responder responder, McpConnection connection) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        String resourceUri = params.getString("uri");
        if (resourceUri == null) {
            responder.sendError(id, JsonRPC.INVALID_PARAMS, "Resource URI not defined");
            return;
        }
        LOG.debugf("Read resource %s [id: %s]", resourceUri, id);

        ArgumentProviders argProviders = new ArgumentProviders(Map.of(), connection, id, resourceUri, responder);

        try {
            Future<ResourceResponse> fu = manager.execute(resourceUri, argProviders);
            fu.onComplete(new Handler<AsyncResult<ResourceResponse>>() {
                @Override
                public void handle(AsyncResult<ResourceResponse> ar) {
                    if (ar.succeeded()) {
                        ResourceResponse resourceResponse = ar.result();
                        responder.sendResult(id, resourceResponse);
                    } else {
                        Throwable cause = ar.cause();
                        if (cause instanceof McpException mcp) {
                            responder.sendError(id, mcp.getJsonRpcError(), mcp.getMessage());
                        } else {
                            LOG.errorf(ar.cause(), "Unable to read resource %s", resourceUri);
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
