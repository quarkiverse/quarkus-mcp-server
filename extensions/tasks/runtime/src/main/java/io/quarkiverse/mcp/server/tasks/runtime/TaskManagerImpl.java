package io.quarkiverse.mcp.server.tasks.runtime;

import java.time.Duration;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.tasks.TaskManager;
import io.quarkiverse.mcp.server.tasks.runtime.config.McpTasksServerRuntimeConfig;
import io.quarkiverse.mcp.server.tasks.runtime.config.McpTasksServersRuntimeConfig;
import io.vertx.core.Vertx;

/**
 * In-memory registry of the tasks created via {@link io.quarkiverse.mcp.server.tasks.Tasks}.
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
    private final McpTasksServersRuntimeConfig config;

    TaskManagerImpl(McpTasksServersRuntimeConfig config, Vertx vertx) {
        this.tasks = new ConcurrentHashMap<>();
        this.config = config;
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
     *
     * @param ttl the time-to-live, or {@code null} to use the configured default; zero or negative means unlimited
     * @param pollInterval the polling interval, or {@code null} to use the configured default
     * @param statusMessage the initial status message, may be {@code null}
     */
    TaskImpl create(String toolName, String serverName, Duration ttl, Duration pollInterval, String statusMessage) {
        McpTasksServerRuntimeConfig serverConfig = config.servers().get(serverName);
        if (ttl == null) {
            ttl = serverConfig.tasks().defaultTtl();
        }
        if (ttl.isZero() || ttl.isNegative()) {
            // Unlimited
            ttl = null;
        }
        if (pollInterval == null) {
            pollInterval = serverConfig.tasks().defaultPollInterval();
        }
        if (pollInterval.isZero() || pollInterval.isNegative()) {
            throw new IllegalStateException("The task poll interval must be positive: " + pollInterval);
        }
        TaskImpl task = new TaskImpl(UUID.randomUUID().toString(), serverName, toolName, ttl, pollInterval, statusMessage);
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

    boolean remove(TaskImpl task) {
        if (tasks.remove(task.id(), task)) {
            LOG.debugf("Task %s removed [%s]", task.id(), task.status().jsonValue());
            return true;
        }
        return false;
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
