package io.quarkiverse.mcp.server.tasks.spi;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.quarkiverse.mcp.server.tasks.TaskStatus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * JSON-related logic of {@link TaskRecord}: normalization of JSON values and the conversion to/from a single JSON document.
 */
final class TaskRecordJson {

    // A marker returned when a non-JSON value is found
    private static final Object NOT_JSON = new Object();

    private TaskRecordJson() {
    }

    /**
     * @see TaskRecord#toJson()
     */
    static JsonObject toJson(TaskRecord record) {
        // Note that the accessors return a copy of JSON values
        JsonObject json = new JsonObject()
                .put("id", record.id())
                .put("serverName", record.serverName())
                .put("toolName", record.toolName())
                .put("executionNodeId", record.executionNodeId())
                .put("version", record.version())
                .put("status", record.status().jsonValue())
                .put("createdAt", record.createdAt().toEpochMilli())
                .put("lastUpdatedAt", record.lastUpdatedAt().toEpochMilli())
                .put("pollIntervalMs", record.pollInterval().toMillis())
                .put("inputRequests", record.inputRequests())
                .put("outstandingInputRequestKeys", new JsonArray(record.outstandingInputRequestKeys()));
        putIfNotNull(json, "statusMessage", record.statusMessage());
        putIfNotNull(json, "ttlMs", record.ttl().map(Duration::toMillis).orElse(null));
        putIfNotNull(json, "result", record.result());
        putIfNotNull(json, "error", record.error());
        putIfNotNull(json, "inputResponses", record.inputResponses());
        putIfNotNull(json, "cancellationReason", record.cancellationReason());
        return json;
    }

    /**
     * @see TaskRecord#fromJson(JsonObject)
     */
    static TaskRecord fromJson(JsonObject json) {
        TaskRecord.Builder builder = TaskRecord.builder()
                .id(json.getString("id"))
                .serverName(json.getString("serverName"))
                .toolName(json.getString("toolName"))
                .executionNodeId(json.getString("executionNodeId"))
                .version(json.getLong("version", 0L))
                .status(TaskStatus.from(json.getString("status")))
                .statusMessage(json.getString("statusMessage"))
                .createdAt(Instant.ofEpochMilli(json.getLong("createdAt")))
                .lastUpdatedAt(Instant.ofEpochMilli(json.getLong("lastUpdatedAt")))
                .pollInterval(Duration.ofMillis(json.getLong("pollIntervalMs")))
                .result(json.getJsonObject("result"))
                .error(json.getJsonObject("error"))
                .inputRequests(json.getJsonObject("inputRequests"))
                .inputResponses(json.getJsonObject("inputResponses"))
                .cancellationReason(json.getString("cancellationReason"));
        Long ttlMs = json.getLong("ttlMs");
        if (ttlMs != null) {
            builder.ttl(Duration.ofMillis(ttlMs));
        }
        JsonArray keys = json.getJsonArray("outstandingInputRequestKeys");
        if (keys != null) {
            builder.outstandingInputRequestKeys(keys.stream().map(Object::toString).toList());
        }
        return builder.build();
    }

    /**
     * Returns a new object that contains JSON types only.
     * <p>
     * If the given value already contains JSON types only (which is the case for most values, e.g. decoded JSON messages)
     * a deep copy is created. Otherwise, the value is encoded and decoded. Unlike {@link JsonObject#copy()}, this also works
     * for arbitrary values (POJOs, Jackson nodes, etc.) that can be serialized by Jackson.
     *
     * @param json the value, may be {@code null}
     * @return a new normalized object, or {@code null} if the given value is {@code null}
     */
    static JsonObject normalize(JsonObject json) {
        if (json == null) {
            return null;
        }
        Object copy = copyJson(json);
        return copy != NOT_JSON ? (JsonObject) copy : new JsonObject(json.toBuffer());
    }

    /**
     * Merges the normalized {@code added} value with the {@code current} value. The {@code current} value is never modified.
     *
     * @param current the current normalized value, may be {@code null}
     * @param added the added value, may be {@code null}
     * @return the merged value
     */
    static JsonObject merge(JsonObject current, JsonObject added) {
        JsonObject normalized = normalize(added);
        if (current == null || current.isEmpty()) {
            return normalized != null ? normalized : current;
        }
        if (normalized == null || normalized.isEmpty()) {
            return current;
        }
        // The current value may be shared with a record (see TaskRecord#toBuilder()) but it's normalized, i.e. copy() is safe
        return current.copy().mergeIn(normalized);
    }

    /**
     * Only safe for normalized values, i.e. values that contain JSON types only.
     *
     * @param json the normalized value, may be {@code null}
     * @return a copy of the given value, or {@code null}
     */
    static JsonObject copy(JsonObject json) {
        return json != null ? json.copy() : null;
    }

    /**
     * @return a deep copy of the given value, or {@link #NOT_JSON} if the value contains a non-JSON type
     */
    private static Object copyJson(Object value) {
        // The types produced when decoding JSON
        if (value == null
                || value instanceof String
                || value instanceof Boolean
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Double
                || value instanceof BigInteger
                || value instanceof BigDecimal) {
            return value;
        }
        Map<?, ?> map = value instanceof JsonObject object ? object.getMap() : value instanceof Map<?, ?> m ? m : null;
        if (map != null) {
            Map<String, Object> copy = new LinkedHashMap<>(Math.max(16, (int) (map.size() / 0.75f) + 1));
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!(e.getKey() instanceof String key)) {
                    return NOT_JSON;
                }
                Object val = copyJson(e.getValue());
                if (val == NOT_JSON) {
                    return NOT_JSON;
                }
                copy.put(key, val);
            }
            return new JsonObject(copy);
        }
        List<?> list = value instanceof JsonArray array ? array.getList() : value instanceof List<?> l ? l : null;
        if (list != null) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                Object val = copyJson(element);
                if (val == NOT_JSON) {
                    return NOT_JSON;
                }
                copy.add(val);
            }
            return new JsonArray(copy);
        }
        return NOT_JSON;
    }

    private static void putIfNotNull(JsonObject json, String key, Object value) {
        if (value != null) {
            json.put(key, value);
        }
    }

}
