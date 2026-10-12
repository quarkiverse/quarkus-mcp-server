package io.quarkiverse.mcp.server.tasks;

import java.time.Duration;
import java.util.function.Function;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.ExecutionModel;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpResultException;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolResponse;
import io.smallrye.mutiny.Uni;

/**
 * Makes it possible for a {@link Tool} method to execute its work asynchronously as a <em>task</em>, as defined by the
 * <a href="https://modelcontextprotocol.io/extensions/tasks/overview">MCP Tasks</a> extension
 * ({@code io.modelcontextprotocol/tasks}).
 * <p>
 * A {@code Tasks} parameter may be declared by any {@link Tool} method. If the calling client declared the tasks extension
 * capability ({@link #isSupported()}), the tool can create a task instead of blocking until the work finishes:
 *
 * <pre>
 * &#64;Tool
 * ToolResponse runPipeline(String repo, Tasks tasks) {
 *     if (!tasks.isSupported()) {
 *         return run(repo);
 *     }
 *     throw tasks.newTask()
 *             .setHandler(task -> run(repo), false)
 *             .create();
 * }
 * </pre>
 *
 * The server durably creates the task, executes the handler asynchronously and immediately responds with a
 * {@code CreateTaskResult} ({@code resultType: "task"}) that carries the task id - the {@link CreateTaskException} returned by
 * {@link TaskDefinition#create()} is an {@link McpResultException} converted to that result by the server. The client then
 * polls the task with {@code tasks/get} until it reaches a terminal status ({@code completed}, {@code failed} or
 * {@code cancelled}) and retrieves the final {@link ToolResponse} from the {@code result} field.
 * <p>
 * The handler receives a {@link TaskContext} to report progress via a status message, to request input from the client
 * (elicitation, sampling, roots) while the task is running, and to observe a cancellation requested via {@code tasks/cancel}.
 *
 * @see TaskContext
 * @see TaskManager
 */
public interface Tasks {

    /**
     * @return {@code true} if the client declared the tasks extension capability, {@code false} otherwise
     */
    boolean isSupported();

    /**
     * Returns a new task definition. The handler must be set with {@link TaskDefinition#setHandler(Function, boolean)} or
     * {@link TaskDefinition#setAsyncHandler(Function)}.
     *
     * @return a new task definition
     * @throws io.quarkiverse.mcp.server.McpException with code {@link JsonRpcErrorCodes#MISSING_REQUIRED_CLIENT_CAPABILITY}
     *         if the client did not declare the tasks extension capability
     * @see #isSupported()
     */
    TaskDefinition newTask();

    /**
     * Defines the work executed by a task. It follows the pattern of the programmatic feature definitions such as
     * {@code ToolManager.ToolDefinition}: the handler determines the {@link ExecutionModel}.
     */
    interface TaskDefinition {

        /**
         * Sets the time-to-live of the task, measured from its creation. Once elapsed, the task may be discarded by the
         * server, including its result. It is advertised to the client as {@code ttlMs}. A zero or negative duration means
         * <em>unlimited</em> ({@code ttlMs: null}).
         * <p>
         * If not set, {@code quarkus.mcp.server.tasks.default-ttl} is used.
         *
         * @param ttl the time-to-live
         * @return self
         */
        TaskDefinition setTtl(Duration ttl);

        /**
         * Sets the polling interval suggested to the client, advertised as {@code pollIntervalMs}. It must be positive.
         * <p>
         * If not set, {@code quarkus.mcp.server.tasks.default-poll-interval} is used.
         *
         * @param pollInterval the polling interval
         * @return self
         */
        TaskDefinition setPollInterval(Duration pollInterval);

        /**
         * Sets the initial status message of the task.
         *
         * @param statusMessage the status message
         * @return self
         * @see TaskContext#setStatusMessage(String)
         */
        TaskDefinition setStatusMessage(String statusMessage);

        /**
         * Sets a blocking handler. It is executed on a worker thread ({@link ExecutionModel#WORKER_THREAD}), or on a
         * virtual thread ({@link ExecutionModel#VIRTUAL_THREAD}) if {@code runOnVirtualThread} is {@code true}.
         * <p>
         * The handler may throw a {@link io.quarkiverse.mcp.server.ToolCallException} to complete the task with an error
         * {@link ToolResponse}, an {@link io.quarkiverse.mcp.server.McpException} to fail the task with a JSON-RPC error, or
         * a {@link Cancellation.OperationCancellationException} to cancel the task.
         *
         * @param handler the handler
         * @param runOnVirtualThread if {@code true} the handler is executed on a virtual thread
         * @return self
         */
        TaskDefinition setHandler(Function<TaskContext, ToolResponse> handler, boolean runOnVirtualThread);

        /**
         * Sets a non-blocking handler. It is executed on the event loop ({@link ExecutionModel#EVENT_LOOP}) and must not
         * block.
         *
         * @param handler the handler
         * @return self
         * @see #setHandler(Function, boolean)
         */
        TaskDefinition setAsyncHandler(Function<TaskContext, Uni<ToolResponse>> handler);

        /**
         * Durably creates the task and starts the handler asynchronously.
         * <p>
         * The returned exception must be thrown from the tool method so that the server responds with the
         * {@code CreateTaskResult}; see {@link Tasks}. A non-blocking tool may use {@link #createAsync()} instead.
         *
         * @return the exception that produces the {@code CreateTaskResult}
         * @throws IllegalStateException if no handler is set
         */
        CreateTaskException create();

        /**
         * Durably creates the task and starts the handler asynchronously. This is a convenience method for non-blocking
         * tools; it is equivalent to {@code Uni.createFrom().failure(create())}.
         *
         * @return a failed {@link Uni} that produces the {@code CreateTaskResult}
         * @see #create()
         */
        default Uni<ToolResponse> createAsync() {
            return Uni.createFrom().failure(create());
        }

    }

}
