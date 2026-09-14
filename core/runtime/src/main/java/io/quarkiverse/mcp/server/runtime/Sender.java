package io.quarkiverse.mcp.server.runtime;

import static io.quarkiverse.mcp.server.runtime.Messages.newError;
import static io.quarkiverse.mcp.server.runtime.Messages.newResult;

import java.util.Map;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface Sender {

    Future<Void> send(JsonObject message);

    default Future<Void> sendEmptyResult(Object id) {
        return send(newResult(id, new JsonObject()));
    }

    default Future<Void> sendResult(Object id, Object result) {
        return sendResult(id, result, null);
    }

    @SuppressWarnings("unchecked")
    default Future<Void> sendResult(Object id, Object result, JsonObject responseMeta) {
        JsonObject message = newResult(id, result);
        if (result instanceof JsonObject resultJson) {
            if (!resultJson.containsKey("resultType")) {
                resultJson.put("resultType", "complete");
            }
            if (responseMeta != null) {
                JsonObject existingMeta = resultJson.getJsonObject("_meta");
                if (existingMeta != null) {
                    existingMeta.mergeIn(responseMeta);
                } else {
                    resultJson.put("_meta", responseMeta.copy());
                }
            }
        } else if (result instanceof Map resultMap) {
            if (!resultMap.containsKey("resultType")) {
                resultMap.put("resultType", "complete");
            }
            if (responseMeta != null) {
                Object existingMeta = resultMap.get("_meta");
                if (existingMeta instanceof Map existingMetaMap) {
                    existingMetaMap.putAll(responseMeta.getMap());
                } else {
                    resultMap.put("_meta", responseMeta.copy().getMap());
                }
            }
        }
        return send(message);
    }

    default Future<Void> sendError(Object id, int code, String message) {
        return send(newError(id, code, message));
    }

    default Future<Void> sendInternalError(Object id) {
        return sendError(id, JsonRpcErrorCodes.INTERNAL_ERROR, "Internal error");
    }

}
