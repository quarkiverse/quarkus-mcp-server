package io.quarkiverse.mcp.server.runtime;

import java.util.Map;
import java.util.Objects;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.TaskManager;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Handles the {@code tasks/get}, {@code tasks/update} and {@code tasks/cancel} requests of the MCP Tasks extension.
 */
class TaskMessageHandler extends MessageHandler {

    private static final Logger LOG = Logger.getLogger(TaskMessageHandler.class);

    static final String CANCELLED_BY_CLIENT = "Cancellation requested by the client";

    private final TaskManagerImpl manager;

    TaskMessageHandler(TaskManagerImpl manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    Future<Void> tasksGet(JsonObject message, McpRequest mcpRequest, JsonObject responseMeta) {
        Object id = Messages.getId(message);
        if (!supportsTasks(mcpRequest)) {
            return sendMissingCapability(id, mcpRequest);
        }
        TaskImpl task = findTask(message, mcpRequest, "Failed to retrieve task");
        if (task == null) {
            return Future.succeededFuture();
        }
        LOG.debugf("Get task %s [id: %s]", task.id(), id);
        return mcpRequest.sender().sendResult(id, task.toJson(true), responseMeta);
    }

    Future<Void> tasksUpdate(JsonObject message, McpRequest mcpRequest, JsonObject responseMeta) {
        Object id = Messages.getId(message);
        if (!supportsTasks(mcpRequest)) {
            return sendMissingCapability(id, mcpRequest);
        }
        TaskImpl task = findTask(message, mcpRequest, "Failed to update task");
        if (task == null) {
            return Future.succeededFuture();
        }
        JsonObject inputResponses = Messages.getParams(message).getJsonObject("inputResponses");
        if (inputResponses == null) {
            return mcpRequest.sender().sendError(id, JsonRpcErrorCodes.INVALID_PARAMS,
                    "Failed to update task: Missing required param: inputResponses");
        }
        LOG.debugf("Update task %s [id: %s]", task.id(), id);
        task.updateInputResponses(inputResponses);
        // Empty acknowledgement
        return mcpRequest.sender().sendResult(id, new JsonObject(), responseMeta);
    }

    Future<Void> tasksCancel(JsonObject message, McpRequest mcpRequest, JsonObject responseMeta) {
        Object id = Messages.getId(message);
        if (!supportsTasks(mcpRequest)) {
            return sendMissingCapability(id, mcpRequest);
        }
        TaskImpl task = findTask(message, mcpRequest, "Failed to cancel task");
        if (task == null) {
            return Future.succeededFuture();
        }
        LOG.debugf("Cancel task %s [id: %s]", task.id(), id);
        task.cancel(CANCELLED_BY_CLIENT);
        // Empty acknowledgement
        return mcpRequest.sender().sendResult(id, new JsonObject(), responseMeta);
    }

    /**
     * @return the task, or {@code null} if an error response was sent
     */
    private TaskImpl findTask(JsonObject message, McpRequest mcpRequest, String errorPrefix) {
        Object id = Messages.getId(message);
        JsonObject params = Messages.getParams(message);
        String taskId = params != null ? params.getString("taskId") : null;
        if (taskId == null) {
            mcpRequest.sender().sendError(id, JsonRpcErrorCodes.INVALID_PARAMS,
                    errorPrefix + ": Missing required param: taskId");
            return null;
        }
        TaskImpl task = manager.get(taskId, mcpRequest.serverName());
        if (task == null) {
            mcpRequest.sender().sendError(id, JsonRpcErrorCodes.INVALID_PARAMS, errorPrefix + ": Task not found");
            return null;
        }
        if (task.isExpired()) {
            manager.remove(task);
            mcpRequest.sender().sendError(id, JsonRpcErrorCodes.INVALID_PARAMS, errorPrefix + ": Task has expired");
            return null;
        }
        return task;
    }

    static boolean supportsTasks(McpRequest mcpRequest) {
        InitialRequest initialRequest = mcpRequest.connection().initialRequest();
        return initialRequest != null && initialRequest.supportsExtension(TaskManager.EXTENSION_ID);
    }

    static Future<Void> sendMissingCapability(Object id, McpRequest mcpRequest) {
        mcpRequest.setTracingErrorResponse(false, JsonRpcErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY,
                "Missing required client capability");
        return mcpRequest.sender().send(Messages.newError(id, JsonRpcErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY,
                "Missing required client capability: " + TaskManager.EXTENSION_ID, missingCapabilityData()));
    }

    static Map<String, Object> missingCapabilityData() {
        return Map.of("requiredCapabilities", Map.of("extensions", Map.of(TaskManager.EXTENSION_ID, Map.of())));
    }

}
