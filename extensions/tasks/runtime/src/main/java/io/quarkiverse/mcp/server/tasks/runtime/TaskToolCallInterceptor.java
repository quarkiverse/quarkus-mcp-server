package io.quarkiverse.mcp.server.tasks.runtime;

import java.util.Map;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.runtime.McpRequest;
import io.quarkiverse.mcp.server.runtime.Messages;
import io.quarkiverse.mcp.server.runtime.ToolCallInterceptor;
import io.quarkiverse.mcp.server.tasks.TaskContext;
import io.quarkiverse.mcp.server.tasks.TaskOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Executes a task-augmented tool as a task if the client declared the tasks extension capability.
 */
@Singleton
public class TaskToolCallInterceptor implements ToolCallInterceptor {

    private static final Logger LOG = Logger.getLogger(TaskToolCallInterceptor.class);

    private final TaskManagerImpl manager;

    TaskToolCallInterceptor(TaskManagerImpl manager) {
        this.manager = manager;
    }

    @Override
    public Future<Void> intercept(ToolCall toolCall) {
        McpRequest mcpRequest = toolCall.mcpRequest();
        String toolName = toolCall.tool().name();
        TaskOptions options = manager.getTaskOptions(toolName, mcpRequest.serverName()).orElse(null);
        if (options == null) {
            // Not a task-augmented tool
            return null;
        }
        Object id = toolCall.requestId();
        if (!TasksExtension.supportsTasks(mcpRequest)) {
            if (options.required()) {
                LOG.debugf("Tool %s requires the tasks extension capability [id: %s]", toolName, id);
                McpException e = TasksExtension.missingCapability();
                mcpRequest.setTracingErrorResponse(false, e.getJsonRpcErrorCode(), e.getMessage());
                return mcpRequest.sender().send(Messages.newError(id, e.getJsonRpcErrorCode(), e.getMessage(), e.getData()));
            }
            // Fall back to the synchronous execution
            return toolCall.proceed(Map.of(TaskContext.class, TaskImpl.NoTaskContext.INSTANCE));
        }

        // The task must be durably created before the response is sent
        TaskImpl task = manager.create(toolName, options, mcpRequest.serverName());
        LOG.debugf("Call tool %s as task %s [id: %s]", toolName, task.id(), id);
        JsonObject createTaskResult = task.toJson(false).put("resultType", "task");
        Future<Void> ret = mcpRequest.sender().sendResult(id, createTaskResult, toolCall.responseMeta());

        // Execute the tool asynchronously; the task is updated once it completes
        toolCall.executeDetached(Map.of(TaskContext.class, task, Cancellation.class, task.cancellation()))
                .onComplete(r -> {
                    if (r.succeeded()) {
                        task.complete(JsonObject.mapFrom(r.result()));
                    } else {
                        JsonObject response = toolCall.failureResponse(r.cause());
                        if (response == null) {
                            // The operation was cancelled - no-op if the task was already cancelled via tasks/cancel
                            task.cancel(null);
                        } else if (response.containsKey("result")) {
                            task.complete(response.getJsonObject("result"));
                        } else {
                            JsonObject error = response.getJsonObject("error");
                            task.fail(error.getInteger("code"), error.getString("message"), error.getValue("data"));
                        }
                    }
                });
        return ret;
    }

}
