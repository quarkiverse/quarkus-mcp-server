package io.quarkiverse.mcp.server.runtime;

import java.util.Map;
import java.util.Objects;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.ResourceManager.ResourceInfo;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.runtime.FeatureManagerBase.FeatureExecutionContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ResourceMessageHandler extends MessageHandler {

    private static final Logger LOG = Logger.getLogger(ResourceMessageHandler.class);

    private final ResourceManagerImpl manager;

    private final int pageSize;

    ResourceMessageHandler(ResourceManagerImpl manager, int pageSize) {
        this.manager = Objects.requireNonNull(manager);
        this.pageSize = pageSize;
    }

    void resourcesSubscribe(JsonObject message, Sender sender, McpConnection connection) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        String resourceUri = params.getString("uri");
        if (resourceUri == null) {
            sender.sendError(id, JsonRPC.INVALID_PARAMS, "Resource URI not defined");
            return;
        }
        LOG.debugf("Subscribe to resource %s [id: %s]", resourceUri, id);
        manager.subscribe(resourceUri, connection.id());
    }

    void resourcesUnsubscribe(JsonObject message, Sender sender, McpConnection connection) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        String resourceUri = params.getString("uri");
        if (resourceUri == null) {
            sender.sendError(id, JsonRPC.INVALID_PARAMS, "Resource URI not defined");
            return;
        }
        LOG.debugf("Unsubscribe to resource %s [id: %s]", resourceUri, id);
        manager.unsubscribe(resourceUri, connection.id());
    }

    void resourcesList(JsonObject message, Sender sender) {
        Object id = message.getValue("id");
        Cursor cursor = Messages.getCursor(message, sender);

        LOG.debugf("List resources [id: %s, cursor: %s]", id, cursor);

        JsonArray resources = new JsonArray();
        JsonObject result = new JsonObject().put("resources", resources);
        Page<ResourceInfo> page = manager.fetchPage(cursor, pageSize);
        for (ResourceInfo info : page) {
            resources.add(info.asJson());
        }
        if (page.hasNextCursor()) {
            ResourceInfo last = page.lastInfo();
            result.put("nextCursor", Cursor.encode(last.createdAt(), last.name()));
        }
        sender.sendResult(id, result);
    }

    void resourcesRead(JsonObject message, Sender sender, McpConnection connection, SecuritySupport securitySupport) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        String resourceUri = params.getString("uri");
        if (resourceUri == null) {
            sender.sendError(id, JsonRPC.INVALID_PARAMS, "Resource URI not defined");
            return;
        }
        LOG.debugf("Read resource %s [id: %s]", resourceUri, id);

        ArgumentProviders argProviders = new ArgumentProviders(Map.of(), connection, id, resourceUri, sender,
                Messages.getProgressToken(message));
        try {
            Future<ResourceResponse> fu = manager.execute(resourceUri,
                    new FeatureExecutionContext(argProviders, securitySupport));
            fu.onComplete(new Handler<AsyncResult<ResourceResponse>>() {
                @Override
                public void handle(AsyncResult<ResourceResponse> ar) {
                    if (ar.succeeded()) {
                        ResourceResponse resourceResponse = ar.result();
                        sender.sendResult(id, resourceResponse);
                    } else {
                        handleFailure(id, sender, connection, ar.cause(), LOG, "Unable to read resource %s", resourceUri);
                    }
                }
            });
        } catch (McpException e) {
            sender.sendError(id, e.getJsonRpcError(), e.getMessage());
        }
    }

}
