package io.quarkiverse.mcp.server.tasks;

import java.time.Duration;

/**
 * The options of a task-augmented tool; the programmatic counterpart of the {@link Task} annotation.
 *
 * @param required if {@code true}, the tool can only be executed as a task; see {@link Task#required()}
 * @param ttl the time-to-live of a task, or {@code null} to use {@code quarkus.mcp.server.tasks.default-ttl}; a zero or
 *        negative duration means unlimited
 * @param pollInterval the polling interval suggested to the client, or {@code null} to use
 *        {@code quarkus.mcp.server.tasks.default-poll-interval}; must be positive if set
 * @see Task
 * @see TaskManager#setTaskOptions(String, String, TaskOptions)
 */
public record TaskOptions(boolean required, Duration ttl, Duration pollInterval) {

    public TaskOptions {
        if (pollInterval != null && (pollInterval.isZero() || pollInterval.isNegative())) {
            throw new IllegalArgumentException("pollInterval must be positive");
        }
    }

    /**
     * @return the options of a task-augmented tool that falls back to synchronous execution and uses the configured defaults
     */
    public static TaskOptions defaults() {
        return new TaskOptions(false, null, null);
    }

}
