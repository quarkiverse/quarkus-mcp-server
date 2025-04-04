package io.quarkiverse.mcp.server.runtime;

import org.jboss.logging.Logger;

import io.vertx.core.json.JsonObject;

public class Messages {

    private static final Logger LOG = Logger.getLogger(Messages.class);

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
        JsonObject ret = new JsonObject()
                .put("jsonrpc", JsonRPC.VERSION)
                .put("method", method);
        if (params != null) {
            ret.put("params", params);
        }
        return ret;
    }

    public static JsonObject newNotification(String method) {
        return newNotification(method, null);
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

    static Cursor getCursor(JsonObject message, Responder responder) {
        JsonObject params = message.getJsonObject("params");
        if (params != null) {
            String cursorVal = params.getString("cursor");
            if (cursorVal != null) {
                try {
                    return Cursor.decode(cursorVal);
                } catch (Exception e) {
                    // Invalid cursors should result in an error with code -32602 (Invalid params).
                    LOG.warnf("Invalid cursor detected %s: %s", cursorVal, e.toString());
                    responder.sendError(message.getValue("id"), JsonRPC.INVALID_PARAMS,
                            "Invalid cursor detected: " + cursorVal);
                    return null;
                }
            }
        }
        return Cursor.FIRST_PAGE;
    }

}
