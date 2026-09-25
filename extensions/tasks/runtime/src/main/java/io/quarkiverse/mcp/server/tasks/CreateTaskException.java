package io.quarkiverse.mcp.server.tasks;

import java.util.Objects;

import io.quarkiverse.mcp.server.McpResultException;
import io.vertx.core.json.JsonObject;

/**
 * Thrown from a tool method to respond with a {@code CreateTaskResult} ({@code resultType: "task"}) instead of the standard
 * {@code CallToolResult}.
 * <p>
 * Instances are obtained from {@link Tasks.TaskDefinition#create()}; the task is already created and running when the
 * exception is thrown.
 *
 * @see Tasks
 */
public final class CreateTaskException extends McpResultException {

    private static final long serialVersionUID = 1L;

    private final TaskManager.TaskInfo task;
    private final transient JsonObject result;

    /**
     * @param task the created task
     * @param result the {@code CreateTaskResult} payload
     */
    public CreateTaskException(TaskManager.TaskInfo task, JsonObject result) {
        super("Task created: " + Objects.requireNonNull(task).id());
        this.task = task;
        this.result = Objects.requireNonNull(result);
    }

    /**
     * @return the created task
     */
    public TaskManager.TaskInfo task() {
        return task;
    }

    @Override
    public JsonObject result() {
        return result;
    }

}
