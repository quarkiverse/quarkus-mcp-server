package io.quarkiverse.mcp.server.tasks.runtime;

import java.util.Map;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpExtension;
import io.quarkiverse.mcp.server.McpExtensionMethod;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.RawMessage;
import io.quarkiverse.mcp.server.tasks.TaskManager;
import io.quarkiverse.mcp.server.tasks.runtime.TaskManagerImpl.Transition;
import io.quarkiverse.mcp.server.tasks.spi.TaskRecord;
import io.smallrye.mutiny.Uni;
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
    public Uni<JsonObject> get(String taskId, McpConnection connection) {
        checkCapability(connection);
        return findTask(taskId, connection, "Failed to retrieve task").map(task -> {
            LOG.debugf("Get task %s", task.id());
            return TaskJson.toJson(task, true);
        });
    }

    @McpExtensionMethod("tasks/update")
    public Uni<JsonObject> update(String taskId, McpConnection connection, RawMessage rawMessage) {
        checkCapability(connection);
        JsonObject params = rawMessage.asJsonObject().getJsonObject("params");
        JsonObject inputResponses = params != null ? params.getJsonObject("inputResponses") : null;
        return findTask(taskId, connection, "Failed to update task").flatMap(task -> {
            if (inputResponses == null) {
                return Uni.createFrom().failure(new McpException(
                        "Failed to update task: Missing required param: inputResponses", JsonRpcErrorCodes.INVALID_PARAMS));
            }
            LOG.debugf("Update task %s", task.id());
            // The task was just obtained from the store - it's only fetched again if modified concurrently
            return acknowledge(manager.update(task, r -> TaskManagerImpl.inputResponses(r, inputResponses)),
                    "Failed to update task");
        });
    }

    @McpExtensionMethod("tasks/cancel")
    public Uni<JsonObject> cancel(String taskId, McpConnection connection) {
        checkCapability(connection);
        return findTask(taskId, connection, "Failed to cancel task").flatMap(task -> {
            LOG.debugf("Cancel task %s", task.id());
            return acknowledge(manager.update(task, r -> TaskManagerImpl.cancelled(r, CANCELLED_BY_CLIENT)),
                    "Failed to cancel task");
        });
    }

    private Uni<TaskRecord> findTask(String taskId, McpConnection connection, String errorPrefix) {
        return manager.find(taskId, connection.serverName()).flatMap(lookup -> {
            if (lookup.expired()) {
                return Uni.createFrom().failure(
                        new McpException(errorPrefix + ": Task has expired", JsonRpcErrorCodes.INVALID_PARAMS));
            }
            if (lookup.record() == null) {
                return Uni.createFrom().failure(notFound(errorPrefix));
            }
            return Uni.createFrom().item(lookup.record());
        });
    }

    /**
     * @return an empty acknowledgement, or a failure if the task was removed in the meantime, e.g. by another server instance
     */
    private static Uni<JsonObject> acknowledge(Uni<Transition> update, String errorPrefix) {
        return update.flatMap(transition -> transition.notFound()
                ? Uni.createFrom().failure(notFound(errorPrefix))
                : Uni.createFrom().item(new JsonObject()));
    }

    private static McpException notFound(String errorPrefix) {
        return new McpException(errorPrefix + ": Task not found", JsonRpcErrorCodes.INVALID_PARAMS);
    }

    private static void checkCapability(McpConnection connection) {
        if (!supportsTasks(connection)) {
            throw missingCapability();
        }
    }

    static boolean supportsTasks(McpConnection connection) {
        InitialRequest initialRequest = connection.initialRequest();
        if (initialRequest == null) {
            return false;
        }
        // The extensions declared by the client are the properties of the "extensions" capability, keyed by id
        ClientCapability extensions = initialRequest.getCapability("extensions");
        return extensions != null && extensions.properties().containsKey(TaskManager.EXTENSION_ID);
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
