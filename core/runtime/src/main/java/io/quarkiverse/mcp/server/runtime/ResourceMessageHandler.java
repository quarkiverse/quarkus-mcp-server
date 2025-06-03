package io.quarkiverse.mcp.server.runtime;

import java.util.Map;
import java.util.Objects;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.ResourceManager.ResourceInfo;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.runtime.FeatureManagerBase.FeatureExecutionContext;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ResourceMessageHandler extends MessageHandler {

    private static final Logger LOG = Logger.getLogger(ResourceMessageHandler.class);

    private final ResourceManagerImpl manager;

    private final McpServersRuntimeConfig config;

    ResourceMessageHandler(ResourceManagerImpl manager, McpServersRuntimeConfig config) {
        this.manager = Objects.requireNonNull(manager);
        this.config = config;
    }

    Future<Void> resourcesSubscribe(JsonObject message, McpRequest mcpRequest) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        String resourceUri = params.getString("uri");
        if (resourceUri == null) {
            return mcpRequest.sender().sendError(id, JsonRPC.INVALID_PARAMS, "Resource URI not defined");
        }
        LOG.debugf("Subscribe to resource %s [id: %s]", resourceUri, id);
        manager.subscribe(resourceUri, mcpRequest);
        return Future.succeededFuture();
    }

    Future<Void> resourcesUnsubscribe(JsonObject message, McpRequest mcpRequest) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        String resourceUri = params.getString("uri");
        if (resourceUri == null) {
            return mcpRequest.sender().sendError(id, JsonRPC.INVALID_PARAMS, "Resource URI not defined");
        }
        LOG.debugf("Unsubscribe to resource %s [id: %s]", resourceUri, id);
        manager.unsubscribe(resourceUri, mcpRequest.connection().id());
        return Future.succeededFuture();
    }

    Future<Void> resourcesList(JsonObject message, McpRequest mcpRequest) {
        Object id = message.getValue("id");
        Cursor cursor = Messages.getCursor(message, mcpRequest.sender());
        if (cursor == null) {
            return Future.succeededFuture();
        }

        LOG.debugf("List resources [id: %s, cursor: %s]", id, cursor);

        McpServerRuntimeConfig serverConfig = config.servers().get(mcpRequest.serverName());
        if (serverConfig == null) {
            throw new IllegalStateException("Server config not found: " + mcpRequest.serverName());
        }
        int pageSize = serverConfig.resources().pageSize();

        JsonArray resources = new JsonArray();
        JsonObject result = new JsonObject().put("resources", resources);
        Page<ResourceInfo> page = manager.fetchPage(mcpRequest, cursor, pageSize);
        for (ResourceInfo info : page) {
            resources.add(info.asJson());
        }
        if (page.hasNextCursor()) {
            ResourceInfo last = page.lastInfo();
            result.put("nextCursor", Cursor.encode(last.createdAt(), last.name()));
        }
        return mcpRequest.sender().sendResult(id, result);
    }

    Future<Void> resourcesRead(JsonObject message, McpRequest mcpRequest) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        String resourceUri = params.getString("uri");
        if (resourceUri == null) {
            return mcpRequest.sender().sendError(id, JsonRPC.INVALID_PARAMS, "Resource URI not defined");
        }
        LOG.debugf("Read resource %s [id: %s]", resourceUri, id);

        ArgumentProviders argProviders = new ArgumentProviders(Map.of(), mcpRequest.connection(), id, resourceUri,
                mcpRequest.sender(),
                Messages.getProgressToken(message), manager.responseHandlers);
        try {
            Future<ResourceResponse> fu = manager.execute(resourceUri,
                    new FeatureExecutionContext(argProviders, mcpRequest));
            return fu.compose(resourceResponse -> mcpRequest.sender().sendResult(id, resourceResponse),
                    cause -> handleFailure(id, mcpRequest.sender(), mcpRequest.connection(), cause, LOG,
                            "Unable to read resource %s", resourceUri));
        } catch (McpException e) {
            return mcpRequest.sender().sendError(id, e.getJsonRpcError(), e.getMessage());
        }
    }

}
