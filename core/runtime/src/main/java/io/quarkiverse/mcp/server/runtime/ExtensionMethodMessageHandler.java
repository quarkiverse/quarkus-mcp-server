package io.quarkiverse.mcp.server.runtime;

import java.util.Objects;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.runtime.FeatureManagerBase.FeatureExecutionContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

class ExtensionMethodMessageHandler extends MessageHandler {

    private static final Logger LOG = Logger.getLogger(ExtensionMethodMessageHandler.class);

    private final ExtensionMethodManagerImpl manager;

    ExtensionMethodMessageHandler(ExtensionMethodManagerImpl manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    Future<Void> extensionCall(JsonObject message, McpRequest mcpRequest, JsonObject responseMeta) {
        Object id = Messages.getId(message);
        String method = message.getString("method");
        LOG.debugf("Call extension method %s [id: %s]", method, id);
        try {
            Future<Object> fu = manager.execute(method, new FeatureExecutionContext(message, mcpRequest));
            return fu.compose(
                    result -> mcpRequest.sender().sendResult(id, toResult(result), responseMeta),
                    cause -> handleFailure(id, mcpRequest.sender(), mcpRequest, cause, LOG,
                            "Unable to call extension method %s", method, responseMeta));
        } catch (McpException e) {
            return mcpRequest.sender().sendError(id, e.getJsonRpcErrorCode(), e.getMessage());
        }
    }

    private static Object toResult(Object result) {
        if (result == null) {
            return new JsonObject();
        }
        if (result instanceof JsonObject) {
            return result;
        }
        // A Map/POJO/record is mapped to a fresh JsonObject; this way sendResult can safely enrich it with
        // resultType/_meta in place (and a caller-owned immutable Map, e.g. Map.of(), is not mutated)
        return JsonObject.mapFrom(result);
    }

}
