package io.quarkiverse.mcp.server.tasks;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.tasks.spi.TaskStore;
import io.smallrye.common.annotation.CheckReturnValue;
import io.smallrye.mutiny.Uni;

/**
 * This manager can be used to inspect and cancel the tasks created via {@link Tasks}, as defined by the
 * <a href="https://modelcontextprotocol.io/extensions/tasks/overview">MCP Tasks</a> extension.
 * <p>
 * The state of the tasks is kept in a {@link TaskStore}; by default, the tasks are kept in memory. A task is discarded once
 * its time-to-live elapses (unless unlimited), regardless of its status.
 *
 * @see Tasks
 * @see TaskContext
 * @see TaskStore
 */
public interface TaskManager {

    /**
     * The identifier of the MCP Tasks extension, used both as the key under {@code capabilities.extensions} advertised by the
     * server and as the key under the {@code extensions} of the client capabilities.
     */
    String EXTENSION_ID = "io.modelcontextprotocol/tasks";

    /**
     * @param taskId the task id
     * @return a new {@link Uni} that completes with the task, or with {@code null} if no such task exists or the task expired
     */
    @CheckReturnValue
    Uni<TaskInfo> getTask(String taskId);

    /**
     * @param serverName the name of the server configuration, or {@code null} to obtain the tasks of all servers
     * @return a new {@link Uni} that completes with the tasks bound to the given server
     */
    @CheckReturnValue
    Uni<Iterable<TaskInfo>> getTasks(String serverName);

    /**
     * @return a new {@link Uni} that completes with the tasks of all servers
     * @see #getTasks(String)
     */
    @CheckReturnValue
    default Uni<Iterable<TaskInfo>> getTasks() {
        return getTasks(null);
    }

    /**
     * Cancels the task with the given id. Cancellation is cooperative: the task moves to the {@link TaskStatus#CANCELLED}
     * status immediately and the underlying tool is notified via {@link Cancellation}, but it is not forced to stop.
     * <p>
     * If the task is executed by another server instance sharing the {@link TaskStore}, the tool is notified once the
     * executing instance observes the cancellation, i.e. within the polling interval of the task.
     *
     * @param taskId the task id
     * @param reason the reason, may be {@code null}
     * @return a new {@link Uni} that completes with {@code true} if the task was cancelled, or with {@code false} if it does
     *         not exist or is already in a terminal status
     */
    @CheckReturnValue
    Uni<Boolean> cancelTask(String taskId, String reason);

    /**
     * An immutable snapshot of the state of a task.
     */
    interface TaskInfo {

        /**
         * @return the unique task id
         */
        String id();

        /**
         * @return the name of the server configuration the task is bound to
         */
        String serverName();

        /**
         * @return the name of the tool that created the task
         */
        String toolName();

        /**
         * @return the current status
         */
        TaskStatus status();

        /**
         * @return the current status message, or {@code null}
         */
        String statusMessage();

        /**
         * @return the time the task was created
         */
        Instant createdAt();

        /**
         * @return the time the task was last updated
         */
        Instant lastUpdatedAt();

        /**
         * @return the time-to-live measured from {@link #createdAt()}, or an empty {@link Optional} if unlimited
         */
        Optional<Duration> ttl();

        /**
         * @return the polling interval suggested to the client
         */
        Duration pollInterval();

    }

}
