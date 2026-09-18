package io.quarkiverse.mcp.server;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * This manager can be used to inspect and cancel the tasks created for {@linkplain Task task-augmented} tools, as defined by
 * the <a href="https://modelcontextprotocol.io/extensions/tasks/overview">MCP Tasks</a> extension.
 * <p>
 * Tasks are kept in memory. A task is discarded once its time-to-live elapses (unless unlimited), regardless of its status.
 *
 * @see Task
 * @see TaskContext
 */
public interface TaskManager extends Iterable<TaskManager.TaskInfo> {

    /**
     * The identifier of the MCP Tasks extension, used both as the key under {@code capabilities.extensions} advertised by the
     * server and as the key under the {@code extensions} of the client capabilities.
     */
    String EXTENSION_ID = "io.modelcontextprotocol/tasks";

    /**
     * @param taskId the task id
     * @return the task, or {@code null} if no such task exists
     */
    TaskInfo getTask(String taskId);

    /**
     * Cancels the task with the given id. Cancellation is cooperative: the task moves to the {@link TaskStatus#CANCELLED}
     * status immediately and the underlying tool is notified via {@link Cancellation}, but it is not forced to stop.
     *
     * @param taskId the task id
     * @param reason the reason, may be {@code null}
     * @return {@code true} if the task was cancelled, {@code false} if it does not exist or is already in a terminal status
     */
    boolean cancelTask(String taskId, String reason);

    /**
     * A snapshot of the state of a task.
     */
    interface TaskInfo {

        /**
         * @return the unique, unguessable task id
         */
        String id();

        /**
         * @return the name of the server configuration the task is bound to
         */
        String serverName();

        /**
         * @return the name of the tool executed by the task
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
