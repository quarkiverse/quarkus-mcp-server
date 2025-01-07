package io.quarkiverse.mcp.server.runtime;

import io.vertx.core.json.JsonObject;

public class JsonRPC {

    public static final String VERSION = "2.0";

    public static final int RESOURCE_NOT_FOUND = -32002;

    public static final int INTERNAL_ERROR = -32603;
    public static final int INVALID_PARAMS = -32602;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_REQUEST = -32600;
    public static final int PARSE_ERROR = -32700;

    public static boolean validate(JsonObject message, Responder responder) {
        Object id = message.getValue("id");
        String jsonrpc = message.getString("jsonrpc");
        if (!VERSION.equals(jsonrpc)) {
            responder.sendError(id, INVALID_REQUEST, "Invalid jsonrpc version: " + jsonrpc);
            return false;
        }
        if (message.getString("method") == null) {
            responder.sendError(id, METHOD_NOT_FOUND, "Method not set");
            return false;
        }
        return true;
    }

}
