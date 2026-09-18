package io.quarkiverse.mcp.server.tasks.runtime;

import java.util.Map;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpExtension;
import io.quarkiverse.mcp.server.McpExtensionMethod;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.RawMessage;
import io.quarkiverse.mcp.server.runtime.McpRequest;
import io.quarkiverse.mcp.server.runtime.Messages;
import io.quarkiverse.mcp.server.tasks.TaskManager;
import io.vertx.core.json.JsonObject;

/**
 * The {@code io.modelcontextprotocol/tasks} extension: advertises the capability and handles the {@code tasks/get},
 * {@code tasks/update} and {@code tasks/cancel} methods.
 */
@Singleton
@McpServer(McpServer.ALL)
@McpExtension(id = TaskManager.EXTENSION_ID)
public class TasksExtension {

    private static final Logger LOG = Logger.getLogger(TasksExtension.class);

    static final String CANCELLED_BY_CLIENT = "Cancellation requested by the client";

    private final TaskManagerImpl manager;

    TasksExtension(TaskManagerImpl manager) {
        this.manager = manager;
    }

    @McpExtensionMethod("tasks/get")
    public JsonObject get(String taskId, McpConnection connection) {
        checkCapability(connection);
        TaskImpl task = findTask(taskId, connection, "Failed to retrieve task");
        LOG.debugf("Get task %s", task.id());
        return task.toJson(true);
    }

    @McpExtensionMethod("tasks/update")
    public JsonObject update(String taskId, McpConnection connection, RawMessage rawMessage) {
        checkCapability(connection);
        TaskImpl task = findTask(taskId, connection, "Failed to update task");
        JsonObject params = Messages.getParams(rawMessage.asJsonObject());
        JsonObject inputResponses = params != null ? params.getJsonObject("inputResponses") : null;
        if (inputResponses == null) {
            throw new McpException("Failed to update task: Missing required param: inputResponses",
                    JsonRpcErrorCodes.INVALID_PARAMS);
        }
        LOG.debugf("Update task %s", task.id());
        task.updateInputResponses(inputResponses);
        // Empty acknowledgement
        return new JsonObject();
    }

    @McpExtensionMethod("tasks/cancel")
    public JsonObject cancel(String taskId, McpConnection connection) {
        checkCapability(connection);
        TaskImpl task = findTask(taskId, connection, "Failed to cancel task");
        LOG.debugf("Cancel task %s", task.id());
        task.cancel(CANCELLED_BY_CLIENT);
        // Empty acknowledgement
        return new JsonObject();
    }

    private TaskImpl findTask(String taskId, McpConnection connection, String errorPrefix) {
        TaskImpl task = manager.get(taskId, connection.serverName());
        if (task == null) {
            throw new McpException(errorPrefix + ": Task not found", JsonRpcErrorCodes.INVALID_PARAMS);
        }
        if (task.isExpired()) {
            manager.remove(task);
            throw new McpException(errorPrefix + ": Task has expired", JsonRpcErrorCodes.INVALID_PARAMS);
        }
        return task;
    }

    private static void checkCapability(McpConnection connection) {
        if (!supportsTasks(connection)) {
            throw missingCapability();
        }
    }

    static boolean supportsTasks(McpRequest mcpRequest) {
        return supportsTasks(mcpRequest.connection());
    }

    static boolean supportsTasks(McpConnection connection) {
        InitialRequest initialRequest = connection.initialRequest();
        return initialRequest != null && initialRequest.supportsExtension(TaskManager.EXTENSION_ID);
    }

    /**
     * @return the {@code -32021} (Missing Required Client Capability) error
     */
    static McpException missingCapability() {
        return new McpException("Missing required client capability: " + TaskManager.EXTENSION_ID,
                JsonRpcErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY,
                Map.of("requiredCapabilities", Map.of("extensions", Map.of(TaskManager.EXTENSION_ID, Map.of()))));
    }

}
