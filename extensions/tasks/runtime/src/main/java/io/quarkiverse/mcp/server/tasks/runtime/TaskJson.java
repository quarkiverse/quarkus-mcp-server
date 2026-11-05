package io.quarkiverse.mcp.server.tasks.runtime;

import java.time.format.DateTimeFormatter;

import io.quarkiverse.mcp.server.tasks.spi.TaskRecord;
import io.vertx.core.json.JsonObject;

/**
 * Produces the wire representation of a task.
 */
final class TaskJson {

    private TaskJson() {
    }

    /**
     * @param record the task
     * @param detailed if {@code true} then the status-specific fields ({@code inputRequests}, {@code result}, {@code error})
     *        are included, i.e. a {@code DetailedTask} is produced
     * @return a new JSON representation of the task
     */
    static JsonObject toJson(TaskRecord record, boolean detailed) {
        JsonObject json = new JsonObject()
                .put("taskId", record.id())
                .put("status", record.status().jsonValue());
        if (record.statusMessage() != null) {
            json.put("statusMessage", record.statusMessage());
        }
        json.put("createdAt", DateTimeFormatter.ISO_INSTANT.format(record.createdAt()));
        json.put("lastUpdatedAt", DateTimeFormatter.ISO_INSTANT.format(record.lastUpdatedAt()));
        if (record.ttl().isPresent()) {
            json.put("ttlMs", record.ttl().get().toMillis());
        } else {
            json.putNull("ttlMs");
        }
        json.put("pollIntervalMs", record.pollInterval().toMillis());
        if (detailed) {
            switch (record.status()) {
                case INPUT_REQUIRED -> {
                    JsonObject inputRequests = new JsonObject();
                    for (String key : record.outstandingInputRequestKeys()) {
                        JsonObject inputRequest = record.inputRequest(key);
                        if (inputRequest != null) {
                            inputRequests.put(key, inputRequest);
                        }
                    }
                    json.put("inputRequests", inputRequests);
                }
                case COMPLETED -> {
                    JsonObject result = record.result();
                    json.put("result", result != null ? result : new JsonObject());
                }
                case FAILED -> {
                    JsonObject error = record.error();
                    json.put("error", error != null ? error : new JsonObject());
                }
                default -> {
                    // no status-specific fields
                }
            }
        }
        return json;
    }

}
