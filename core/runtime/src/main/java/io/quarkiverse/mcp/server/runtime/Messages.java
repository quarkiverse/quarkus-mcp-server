package io.quarkiverse.mcp.server.runtime;

import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.Icon;
import io.quarkiverse.mcp.server.Implementation;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.vertx.core.json.JsonArray;
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
        return newRequest(id, "ping");
    }

    public static JsonObject newRequest(Object id, String method) {
        return newRequest(id, method, null);
    }

    public static JsonObject newRequest(Object id, String method, Object params) {
        JsonObject request = new JsonObject();
        request.put("jsonrpc", JsonRPC.VERSION);
        request.put("id", id);
        request.put("method", method);
        if (params != null) {
            request.put("params", params);
        }
        return request;
    }

    public static boolean isResponse(JsonObject message) {
        return message.containsKey("result") || message.containsKey("error");
    }

    public static boolean isRequest(JsonObject message) {
        return !isResponse(message) && message.getValue("id") != null;
    }

    public static boolean isNotification(JsonObject message) {
        return !isResponse(message) && message.getValue("id") == null;
    }

    static Cursor getCursor(JsonObject message, Sender sender) {
        JsonObject params = message.getJsonObject("params");
        if (params != null) {
            String cursorVal = params.getString("cursor");
            if (cursorVal != null) {
                try {
                    return Cursor.decode(cursorVal);
                } catch (Exception e) {
                    // Invalid cursors should result in an error with code -32602 (Invalid params).
                    LOG.warnf("Invalid cursor detected %s: %s", cursorVal, e.toString());
                    sender.sendError(message.getValue("id"), JsonRpcErrorCodes.INVALID_PARAMS,
                            "Invalid cursor detected: " + cursorVal);
                    return null;
                }
            }
        }
        return Cursor.FIRST_PAGE;
    }

    public static JsonObject getParams(JsonObject message) {
        return message.getJsonObject("params");
    }

    public static JsonObject getArguments(JsonObject params) {
        return params.getJsonObject("arguments");
    }

    static Object getProgressToken(JsonObject message) {
        JsonObject params = getParams(message);
        if (params != null) {
            JsonObject meta = params.getJsonObject("_meta");
            if (meta != null) {
                return meta.getValue("progressToken");
            }
        }
        return null;
    }

    public static JsonObject newLog(LogLevel level, String loggerName, Object data) {
        return new JsonObject()
                .put("level", level.toString().toLowerCase())
                .put("logger", loggerName)
                .put("data", data);
    }

    public static final String PROMPT_REF = "ref/prompt";
    public static final String RESOURCE_REF = "ref/resource";

    public static boolean isPromptRef(String referenceType) {
        return PROMPT_REF.equals(referenceType);
    }

    public static boolean isResourceRef(String referenceType) {
        return RESOURCE_REF.equals(referenceType);
    }

    public static Implementation decodeImplementation(JsonObject implementation) {
        return new Implementation(implementation.getString("name"), implementation.getString("version"),
                implementation.getString("title"), decodeIcons(implementation), implementation.getString("description"),
                implementation.getString("websiteUrl"));
    }

    private static List<Icon> decodeIcons(JsonObject implementation) {
        JsonArray icons = implementation.getJsonArray("icons");
        if (icons != null) {
            List<Icon> ret = new ArrayList<>();
            for (int i = 0; i < icons.size(); i++) {
                JsonObject icon = icons.getJsonObject(i);
                if (icon != null) {
                    ret.add(new Icon(icon.getString("src"),
                            icon.getString("mimeType"),
                            decodeIconSizes(icon),
                            Icon.Theme.from(icon.getString("theme"))));
                }
            }
            return List.copyOf(ret);
        }
        return List.of();
    }

    private static List<String> decodeIconSizes(JsonObject icon) {
        JsonArray sizes = icon.getJsonArray("sizes");
        if (sizes != null) {
            List<String> ret = new ArrayList<>();
            for (int i = 0; i < sizes.size(); i++) {
                ret.add(sizes.getString(i));
            }
            return List.copyOf(ret);
        }
        return List.of();
    }

}
