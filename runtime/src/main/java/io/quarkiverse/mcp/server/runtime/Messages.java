package io.quarkiverse.mcp.server.runtime;

import io.vertx.core.json.JsonObject;

class Messages {

    static JsonObject newResult(Object id, Object result) {
        JsonObject response = new JsonObject();
        response.put("jsonrpc", JsonRPC.VERSION);
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    static JsonObject internalError(Object id) {
        return newError(id, JsonRPC.INTERNAL_ERROR, "Internal error");
    }

    static JsonObject newError(Object id, int code, String message) {
        JsonObject response = new JsonObject();
        response.put("jsonrpc", JsonRPC.VERSION);
        response.put("id", id);
        response.put("error", new JsonObject()
                .put("code", code)
                .put("message", message));
        return response;
    }

}
