package io.quarkiverse.mcp.server.runtime;

import static io.quarkiverse.mcp.server.runtime.Messages.newError;
import static io.quarkiverse.mcp.server.runtime.Messages.newResult;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface Sender {

    Future<Void> send(JsonObject message);

    default Future<Void> sendResult(Object id, Object result) {
        return send(newResult(id, result));
    }

    default Future<Void> sendError(Object id, int code, String message) {
        return send(newError(id, code, message));
    }

    default Future<Void> sendInternalError(Object id) {
        return sendError(id, JsonRPC.INTERNAL_ERROR, "Internal error");
    }

}
