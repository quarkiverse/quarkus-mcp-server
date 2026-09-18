package io.quarkiverse.mcp.server.tasks;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.Tool;

/**
 * Marks a {@link Tool} method as <em>task-augmented</em>, i.e. eligible for asynchronous execution as defined by the
 * <a href="https://modelcontextprotocol.io/extensions/tasks/overview">MCP Tasks</a> extension
 * ({@code io.modelcontextprotocol/tasks}).
 * <p>
 * When a client that declared the tasks extension capability calls the tool, the server does not block until the tool
 * completes. Instead, it durably creates a <em>task</em>, immediately responds with a {@code CreateTaskResult}
 * ({@code resultType: "task"}) that carries the task id, and executes the tool asynchronously. The client then polls the
 * task with {@code tasks/get} until it reaches a terminal status ({@code completed}, {@code failed} or {@code cancelled})
 * and retrieves the final {@code CallToolResult} from the {@code result} field.
 * <p>
 * A task-augmented tool may declare a {@link TaskContext} parameter to report progress via a status message, or to request
 * input from the client (elicitation, sampling, roots) while the task is running. A {@link Cancellation} parameter observes
 * {@code tasks/cancel} requests.
 * <p>
 * If the calling client did not declare the tasks extension capability, the tool is executed synchronously, unless
 * {@link #required()} is set to {@code true}, in which case an error with code
 * {@link JsonRpcErrorCodes#MISSING_REQUIRED_CLIENT_CAPABILITY} is returned.
 * <p>
 * The tasks extension is advertised automatically under {@code capabilities.extensions} of every server configuration as
 * soon as the {@code quarkus-mcp-server-tasks} extension is present.
 *
 * @see TaskContext
 * @see TaskManager
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface Task {

    /**
     * If {@code true}, the tool can only be executed as a task; a call from a client that did not declare the tasks extension
     * capability results in an error with code {@link JsonRpcErrorCodes#MISSING_REQUIRED_CLIENT_CAPABILITY}.
     * <p>
     * If {@code false} (default), such a call is executed synchronously, i.e. the standard {@code CallToolResult} is
     * returned once the tool completes.
     *
     * @return {@code true} if the tool requires the tasks extension capability
     */
    boolean required() default false;

    /**
     * The time-to-live of a task created for this tool, measured from its creation. Once elapsed, the task may be discarded
     * by the server, including its result. It is advertised to the client as {@code ttlMs}.
     * <p>
     * The value is parsed with the same rules as a {@link java.time.Duration} configuration property, e.g. {@code 10m},
     * {@code 2h} or {@code PT30S}. A zero or negative duration means <em>unlimited</em> ({@code ttlMs: null}).
     * <p>
     * If not set, {@code quarkus.mcp.server.tasks.default-ttl} is used.
     *
     * @return the time-to-live
     */
    String ttl() default "";

    /**
     * The polling interval suggested to the client, advertised as {@code pollIntervalMs}.
     * <p>
     * The value is parsed with the same rules as a {@link java.time.Duration} configuration property, e.g. {@code 5s} or
     * {@code PT2S}. It must be positive.
     * <p>
     * If not set, {@code quarkus.mcp.server.tasks.default-poll-interval} is used.
     *
     * @return the suggested polling interval
     */
    String pollInterval() default "";

}
