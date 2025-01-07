package io.quarkiverse.mcp.server.runtime;

import static io.quarkiverse.mcp.server.runtime.Messages.newError;
import static io.quarkiverse.mcp.server.runtime.Messages.newResult;

import io.vertx.core.json.JsonObject;

public interface Responder {

    void send(JsonObject message);

    default void sendResult(Object id, Object result) {
        send(newResult(id, result));
    }

    default void sendError(Object id, int code, String message) {
        send(newError(id, code, message));
    }

    default void sendInternalError(Object id) {
        sendError(id, JsonRPC.INTERNAL_ERROR, "Internal error");
    }

}
