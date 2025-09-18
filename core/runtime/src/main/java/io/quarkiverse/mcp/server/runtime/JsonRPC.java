package io.quarkiverse.mcp.server.runtime;

import static io.quarkiverse.mcp.server.runtime.Messages.isResponse;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.vertx.core.json.JsonObject;

public class JsonRPC {

    public static final String VERSION = "2.0";

    public static boolean validate(JsonObject message, Sender sender) {
        Object id = message.getValue("id");
        String jsonrpc = message.getString("jsonrpc");
        if (!VERSION.equals(jsonrpc)) {
            sender.sendError(id, JsonRpcErrorCodes.INVALID_REQUEST, "Invalid jsonrpc version: " + jsonrpc);
            return false;
        }
        if (!isResponse(message)) {
            if (message.getString("method") == null) {
                sender.sendError(id, JsonRpcErrorCodes.METHOD_NOT_FOUND, "Method not set");
                return false;
            }
        }
        return true;
    }

}
