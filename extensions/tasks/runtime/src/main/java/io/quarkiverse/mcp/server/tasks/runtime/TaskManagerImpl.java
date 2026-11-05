package io.quarkiverse.mcp.server.tasks.runtime;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.tasks.TaskManager;
import io.quarkiverse.mcp.server.tasks.TaskStatus;
import io.quarkiverse.mcp.server.tasks.runtime.config.McpTasksServerRuntimeConfig;
import io.quarkiverse.mcp.server.tasks.runtime.config.McpTasksServersRuntimeConfig;
import io.quarkiverse.mcp.server.tasks.spi.TaskRecord;
import io.quarkiverse.mcp.server.tasks.spi.TaskStore;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Manages the tasks created via {@link io.quarkiverse.mcp.server.tasks.Tasks}.
 * <p>
 * The state of a task is kept in the {@link TaskStore}. Every state transition is a read-modify-write cycle guarded by the
 * {@linkplain TaskRecord#version() version} of the task (optimistic locking). The live state of the tasks executed by this
 * server instance (the pending input request, cancellation actions, etc.) is held by {@link TaskImpl}.
 * <p>
 * A task is bound to the server configuration it was created for. Task ids are random UUIDs and act as bearer tokens, as
 * permitted by the specification.
 */
@Singleton
public class TaskManagerImpl implements TaskManager {

    private static final Logger LOG = Logger.getLogger(TaskManagerImpl.class);

    // Expired tasks are removed every minute; expiry is also checked lazily when a task is accessed
    static final long EXPIRED_TASKS_CHECK_INTERVAL_MS = 60_000;

    // The max number of attempts to apply a state transition in case of concurrent modifications
    static final int MAX_UPDATE_ATTEMPTS = 50;

    // The max interval of the periodic check of the running tasks in a shared store
    static final long MAX_POLL_TICK_MS = 1_000;

    static final String TASK_EXPIRED = "Task expired";

    private final TaskStore store;
    private final McpTasksServersRuntimeConfig config;
    // The identifier of this server instance; generated at each start
    private final String executionNodeId;
    // The tasks executed by this server instance
    private final ConcurrentMap<String, TaskImpl> localTasks;
    // true if the running tasks are being fetched from the store
    private final AtomicBoolean pollInProgress;

    TaskManagerImpl(TaskStore store, McpTasksServersRuntimeConfig config, Vertx vertx) {
        this.store = store;
        this.config = config;
        this.executionNodeId = UUID.randomUUID().toString();
        this.localTasks = new ConcurrentHashMap<>();
        this.pollInProgress = new AtomicBoolean();
        vertx.setPeriodic(EXPIRED_TASKS_CHECK_INTERVAL_MS, id -> removeExpired()
                .subscribe().with(ignored -> {
                }, t -> LOG.errorf(t, "Unable to remove expired tasks")));
        if (store.isShared()) {
            // A shared store may be modified by another server instance
            long tick = pollTick(config);
            LOG.debugf("Running tasks are checked in the shared task store every %s ms", tick);
            vertx.setPeriodic(tick, id -> poll());
        }
    }

    /**
     * @return the smallest default poll interval of all server configurations, but at most one second
     */
    static long pollTick(McpTasksServersRuntimeConfig config) {
        long tick = MAX_POLL_TICK_MS;
        for (McpTasksServerRuntimeConfig serverConfig : config.servers().values()) {
            long pollInterval = serverConfig.tasks().defaultPollInterval().toMillis();
            if (pollInterval > 0) {
                tick = Math.min(tick, pollInterval);
            }
        }
        return tick;
    }

    @Override
    public Uni<TaskInfo> getTask(String taskId) {
        if (taskId == null) {
            return Uni.createFrom().nullItem();
        }
        return store.get(taskId).flatMap(record -> {
            if (record != null && record.isExpired(Instant.now())) {
                return expire(record.id()).replaceWith(Uni.createFrom().nullItem());
            }
            return Uni.createFrom().item(record);
        });
    }

    @Override
    public Uni<Iterable<TaskInfo>> getTasks(String serverName) {
        return store.list(serverName).map(records -> {
            Instant now = Instant.now();
            List<TaskInfo> ret = new ArrayList<>();
            for (TaskRecord record : records) {
                if (!record.isExpired(now)) {
                    ret.add(record);
                }
            }
            return ret;
        });
    }

    @Override
    public Uni<Boolean> cancelTask(String taskId, String reason) {
        if (taskId == null) {
            return Uni.createFrom().item(false);
        }
        return update(taskId, r -> cancelled(r, reason)).map(Transition::changed);
    }

    /**
     * Durably creates a new task in the {@code working} status.
     *
     * @param ttl the time-to-live, or {@code null} to use the configured default; zero or negative means unlimited
     * @param pollInterval the polling interval, or {@code null} to use the configured default
     * @param statusMessage the initial status message, may be {@code null}
     * @return a new {@link Uni} that completes with the task once it is stored
     */
    Uni<TaskImpl> create(String toolName, String serverName, Duration ttl, Duration pollInterval, String statusMessage) {
        return Uni.createFrom().deferred(() -> {
            McpTasksServerRuntimeConfig serverConfig = config.servers().get(serverName);
            Duration taskTtl = ttl != null ? ttl : serverConfig.tasks().defaultTtl();
            if (taskTtl.isZero() || taskTtl.isNegative()) {
                // Unlimited
                taskTtl = null;
            }
            Duration taskPollInterval = pollInterval != null ? pollInterval : serverConfig.tasks().defaultPollInterval();
            if (taskPollInterval.isZero() || taskPollInterval.isNegative()) {
                throw new IllegalStateException("The task poll interval must be positive: " + taskPollInterval);
            }
            TaskRecord record = TaskRecord.builder()
                    .id(UUID.randomUUID().toString())
                    .serverName(serverName)
                    .toolName(toolName)
                    .executionNodeId(executionNodeId)
                    .version(1)
                    .status(TaskStatus.WORKING)
                    .statusMessage(statusMessage)
                    .createdAt(now())
                    .ttl(taskTtl)
                    .pollInterval(taskPollInterval)
                    .build();
            return store.create(record).map(v -> {
                TaskImpl task = new TaskImpl(this, record);
                localTasks.put(task.id(), task);
                LOG.debugf("Task %s created for tool %s [server: %s]", task.id(), toolName, serverName);
                return task;
            });
        });
    }

    /**
     * @return a new {@link Uni} that completes with the task bound to the given server, or with {@code null} if no such task
     *         exists; the task may be expired
     */
    Uni<TaskRecord> get(String taskId, String serverName) {
        if (taskId == null) {
            return Uni.createFrom().nullItem();
        }
        return store.get(taskId).map(r -> r != null && r.serverName().equals(serverName) ? r : null);
    }

    /**
     * Applies the given state transition. The function receives the current state and returns a builder of the new state, or
     * {@code null} if the state should not change; it may throw an exception to abort the transition. The version and the
     * last update time are set automatically.
     * <p>
     * The transition is retried if the task was modified concurrently. The local task, if any, is notified about the new
     * state.
     *
     * @return a new {@link Uni} that completes with the transition
     */
    Uni<Transition> update(String taskId, Function<TaskRecord, TaskRecord.Builder> transition) {
        return update(taskId, transition, 1);
    }

    private Uni<Transition> update(String taskId, Function<TaskRecord, TaskRecord.Builder> transition, int attempt) {
        return store.get(taskId).flatMap(current -> {
            if (current == null) {
                return Uni.createFrom().item(Transition.NOT_FOUND);
            }
            TaskRecord.Builder builder = transition.apply(current);
            if (builder == null) {
                return Uni.createFrom().item(new Transition(current, current));
            }
            TaskRecord updated = builder
                    .version(current.version() + 1)
                    .lastUpdatedAt(now())
                    .build();
            return store.replace(current, updated).flatMap(replaced -> {
                if (replaced) {
                    TaskImpl local = localTasks.get(taskId);
                    if (local != null) {
                        local.onUpdate(updated);
                    }
                    return Uni.createFrom().item(new Transition(current, updated));
                }
                if (attempt >= MAX_UPDATE_ATTEMPTS) {
                    return Uni.createFrom().failure(new IllegalStateException(
                            "Unable to update task " + taskId + " - concurrent modification detected " + attempt + " times"));
                }
                LOG.debugf("Task %s was modified concurrently - retrying the update [attempt: %s]", taskId, attempt);
                return update(taskId, transition, attempt + 1);
            });
        });
    }

    /**
     * Cancels the task (if not already terminal) and removes it from the store.
     */
    Uni<Void> expire(String taskId) {
        return update(taskId, r -> cancelled(r, TASK_EXPIRED))
                .chain(() -> store.delete(taskId))
                .invoke(deleted -> {
                    TaskImpl local = localTasks.get(taskId);
                    if (local != null) {
                        // The local task is notified even if the task was already in a terminal status
                        local.onRemoved(TASK_EXPIRED);
                    }
                    if (deleted) {
                        LOG.debugf("Task %s expired and was removed", taskId);
                    }
                })
                .replaceWithVoid();
    }

    void untrack(TaskImpl task) {
        localTasks.remove(task.id(), task);
    }

    /**
     * Fetches the state of the running tasks that are due, i.e. whose polling interval elapsed, in a single batch and notifies
     * the local tasks. This way, the modifications made by another server instance are propagated to the task handlers.
     */
    void poll() {
        if (!pollInProgress.compareAndSet(false, true)) {
            // The previous batch was not processed yet
            return;
        }
        long now = System.currentTimeMillis();
        Map<String, TaskImpl> due = new HashMap<>();
        for (TaskImpl task : localTasks.values()) {
            if (task.nextRefreshAt() <= now) {
                // The next refresh is scheduled even if the fetch fails so that a failing store is not hammered
                task.scheduleNextRefresh(now);
                due.put(task.id(), task);
            }
        }
        if (due.isEmpty()) {
            pollInProgress.set(false);
            return;
        }
        Uni<Iterable<TaskRecord>> fetch;
        try {
            fetch = store.getAll(due.keySet());
        } catch (Throwable t) {
            fetch = Uni.createFrom().failure(t);
        }
        fetch.subscribe().with(records -> {
            try {
                for (TaskRecord record : records) {
                    TaskImpl task = due.remove(record.id());
                    if (task != null) {
                        refresh(task, record);
                    }
                }
                // The remaining tasks do not exist anymore
                for (TaskImpl task : due.values()) {
                    refresh(task, null);
                }
            } finally {
                pollInProgress.set(false);
            }
        }, t -> {
            LOG.warnf(t, "Unable to check the state of %s running tasks", due.size());
            pollInProgress.set(false);
        });
    }

    private static void refresh(TaskImpl task, TaskRecord record) {
        try {
            task.onRefresh(record);
        } catch (Throwable t) {
            LOG.errorf(t, "Unable to refresh the state of task %s", task.id());
        }
    }

    Uni<Void> removeExpired() {
        Instant now = Instant.now();
        // First cancel the expired tasks executed by this server instance so that the handlers are notified
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (TaskImpl task : localTasks.values()) {
            if (task.record().isExpired(now)) {
                chain = chain.chain(() -> expire(task.id())
                        .onFailure().recoverWithItem(t -> {
                            LOG.errorf(t, "Unable to expire task %s", task.id());
                            return null;
                        }));
            }
        }
        return chain.chain(() -> store.removeExpired(now));
    }

    // State transitions

    static TaskRecord.Builder cancelled(TaskRecord r, String reason) {
        if (r.status().isTerminal()) {
            return null;
        }
        TaskRecord.Builder builder = r.toBuilder()
                .status(TaskStatus.CANCELLED)
                .cancellationReason(reason)
                .outstandingInputRequestKeys(List.of())
                .inputResponses(null);
        if (reason != null) {
            builder.statusMessage(reason);
        }
        return builder;
    }

    static TaskRecord.Builder completed(TaskRecord r, JsonObject result) {
        if (r.status().isTerminal()) {
            LOG.debugf("Task %s is already %s - the result is discarded", r.id(), r.status().jsonValue());
            return null;
        }
        return r.toBuilder()
                .status(TaskStatus.COMPLETED)
                .result(result)
                .outstandingInputRequestKeys(List.of())
                .inputResponses(null);
    }

    static TaskRecord.Builder failed(TaskRecord r, int code, String message, Object data) {
        if (r.status().isTerminal()) {
            LOG.debugf("Task %s is already %s - the error is discarded: %s", r.id(), r.status().jsonValue(), message);
            return null;
        }
        JsonObject error = new JsonObject().put("code", code).put("message", message);
        if (data != null) {
            error.put("data", data);
        }
        TaskRecord.Builder builder = r.toBuilder()
                .status(TaskStatus.FAILED)
                .error(error)
                .outstandingInputRequestKeys(List.of())
                .inputResponses(null);
        if (r.statusMessage() == null) {
            builder.statusMessage(message);
        }
        return builder;
    }

    static TaskRecord.Builder statusMessage(TaskRecord r, String statusMessage) {
        if (r.status().isTerminal()) {
            LOG.debugf("Task %s is already %s - the status message is discarded: %s", r.id(), r.status().jsonValue(),
                    statusMessage);
            return null;
        }
        return r.toBuilder().statusMessage(statusMessage);
    }

    /**
     * Processes the {@code inputResponses} of a {@code tasks/update} request. Responses for keys that are not currently
     * outstanding are ignored. Once all outstanding requests are fulfilled the task moves back to {@code working}.
     */
    static TaskRecord.Builder inputResponses(TaskRecord r, JsonObject inputResponses) {
        if (r.status() != TaskStatus.INPUT_REQUIRED) {
            LOG.debugf("Task %s is not waiting for input - the input responses are ignored", r.id());
            return null;
        }
        List<String> outstanding = new ArrayList<>(r.outstandingInputRequestKeys());
        // Only the accepted responses are normalized and merged with the responses collected so far
        JsonObject accepted = new JsonObject();
        for (String key : inputResponses.fieldNames()) {
            if (outstanding.remove(key)) {
                accepted.put(key, inputResponses.getValue(key));
            } else {
                LOG.debugf("Task %s: ignored input response for unknown or already satisfied key: %s", r.id(), key);
            }
        }
        if (accepted.isEmpty()) {
            return null;
        }
        TaskRecord.Builder builder = r.toBuilder()
                .outstandingInputRequestKeys(outstanding)
                .addInputResponses(accepted);
        if (outstanding.isEmpty()) {
            builder.status(TaskStatus.WORKING);
        }
        return builder;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    /**
     * @param previous the state before the transition, or {@code null} if the task does not exist
     * @param current the state after the transition, or {@code null} if the task does not exist
     */
    record Transition(TaskRecord previous, TaskRecord current) {

        static final Transition NOT_FOUND = new Transition(null, null);

        boolean changed() {
            return current != null && previous != current;
        }

    }

}
