package io.quarkiverse.mcp.server.runtime;

import io.vertx.core.json.JsonObject;

public class Messages {

    public static JsonObject newResult(Object id, Object result) {
        JsonObject response = new JsonObject();
        response.put("jsonrpc", JsonRPC.VERSION);
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    public static JsonObject newError(Object id, int code, String message) {
        JsonObject response = new JsonObject();
        response.put("jsonrpc", JsonRPC.VERSION);
        response.put("id", id);
        response.put("error", new JsonObject()
                .put("code", code)
                .put("message", message));
        return response;
    }

    public static JsonObject newNotification(String method, Object params) {
        return new JsonObject()
                .put("jsonrpc", JsonRPC.VERSION)
                .put("method", method)
                .put("params", params);
    }

    public static JsonObject newPing(Object id) {
        return new JsonObject()
                .put("jsonrpc", JsonRPC.VERSION)
                .put("id", id)
                .put("method", "ping");
    }

    public static boolean isResponse(JsonObject message) {
        return message.containsKey("result") && message.containsKey("error");
    }

}
