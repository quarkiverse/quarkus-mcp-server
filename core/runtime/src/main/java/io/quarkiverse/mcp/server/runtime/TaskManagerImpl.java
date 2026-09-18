package io.quarkiverse.mcp.server.runtime;

import java.time.Duration;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.TaskManager;
import io.quarkiverse.mcp.server.ToolManager.TaskOptions;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * In-memory registry of the tasks created for task-augmented tools (MCP Tasks extension).
 * <p>
 * A task is bound to the server configuration it was created for. Task ids are random UUIDs and act as bearer tokens, as
 * permitted by the specification.
 */
@Singleton
public class TaskManagerImpl implements TaskManager {

    private static final Logger LOG = Logger.getLogger(TaskManagerImpl.class);

    // Expired tasks are removed every minute; expiry is also checked lazily when a task is accessed
    static final long EXPIRED_TASKS_CHECK_INTERVAL_MS = 60_000;

    private final ConcurrentMap<String, TaskImpl> tasks;
    private final McpServersRuntimeConfig config;
    private final ConnectionManager connectionManager;

    TaskManagerImpl(McpServersRuntimeConfig config, ConnectionManager connectionManager, Vertx vertx) {
        this.tasks = new ConcurrentHashMap<>();
        this.config = config;
        this.connectionManager = connectionManager;
        vertx.setPeriodic(EXPIRED_TASKS_CHECK_INTERVAL_MS, id -> removeExpired());
    }

    @Override
    public TaskInfo getTask(String taskId) {
        return taskId != null ? tasks.get(taskId) : null;
    }

    @Override
    public boolean cancelTask(String taskId, String reason) {
        TaskImpl task = taskId != null ? tasks.get(taskId) : null;
        return task != null && task.cancel(reason);
    }

    @Override
    public Iterator<TaskInfo> iterator() {
        return tasks.values().stream().map(TaskInfo.class::cast).iterator();
    }

    /**
     * Durably creates a new task in the {@code working} status.
     */
    TaskImpl create(String toolName, TaskOptions options, String serverName) {
        McpServerRuntimeConfig serverConfig = config.servers().get(serverName);
        if (serverConfig == null) {
            throw new IllegalStateException("Server config not found: " + serverName);
        }
        Duration ttl = options.ttl() != null ? options.ttl() : serverConfig.tasks().defaultTtl();
        if (ttl.isZero() || ttl.isNegative()) {
            // Unlimited
            ttl = null;
        }
        Duration pollInterval = options.pollInterval() != null ? options.pollInterval()
                : serverConfig.tasks().defaultPollInterval();
        if (pollInterval.isZero() || pollInterval.isNegative()) {
            throw new IllegalStateException("The task poll interval must be positive: " + pollInterval);
        }
        TaskImpl task = new TaskImpl(UUID.randomUUID().toString(), serverName, toolName, ttl, pollInterval, this);
        tasks.put(task.id(), task);
        LOG.debugf("Task %s created for tool %s [server: %s]", task.id(), toolName, serverName);
        return task;
    }

    /**
     * @return the task bound to the given server, or {@code null} if it does not exist; the task may be
     *         {@linkplain TaskImpl#isExpired() expired}
     */
    TaskImpl get(String taskId, String serverName) {
        TaskImpl task = taskId != null ? tasks.get(taskId) : null;
        if (task != null && task.serverName().equals(serverName)) {
            return task;
        }
        return null;
    }

    boolean exists(String taskId, String serverName) {
        TaskImpl task = get(taskId, serverName);
        return task != null && !task.isExpired();
    }

    boolean remove(TaskImpl task) {
        if (tasks.remove(task.id(), task)) {
            LOG.debugf("Task %s removed [%s]", task.id(), task.status().jsonValue());
            return true;
        }
        return false;
    }

    /**
     * Pushes the current state of the task to all connections that subscribed to its status notifications via
     * {@code subscriptions/listen}.
     */
    void taskChanged(TaskImpl task) {
        JsonObject notification = null;
        for (McpConnectionBase connection : connectionManager) {
            if (connection.status() != McpConnection.Status.IN_OPERATION
                    || !connection.serverName().equals(task.serverName())
                    || !connection.supportsSubscriptionsListen()) {
                continue;
            }
            if (notification == null) {
                notification = Messages.newNotification(McpMethod.NOTIFICATIONS_TASKS.jsonRpcName(), task.toJson(true));
            }
            connection.sendNotification(notification, task.id());
        }
    }

    void removeExpired() {
        if (tasks.isEmpty()) {
            return;
        }
        for (TaskImpl task : tasks.values()) {
            if (task.isExpired()) {
                if (!task.status().isTerminal()) {
                    task.cancel("Task expired");
                }
                remove(task);
            }
        }
    }

}
