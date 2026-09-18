package io.quarkiverse.mcp.server.runtime;

import static io.quarkiverse.mcp.server.runtime.Messages.getParams;

import java.util.Objects;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpResultException;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolManager.TaskOptions;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.runtime.FeatureManagerBase.FeatureExecutionContext;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.common.vertx.VertxContext;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ToolMessageHandler extends MessageHandler {

    private static final Logger LOG = Logger.getLogger(ToolMessageHandler.class);

    private final ToolManagerImpl manager;

    private final TaskManagerImpl taskManager;

    private final McpServersRuntimeConfig config;

    ToolMessageHandler(ToolManagerImpl manager, TaskManagerImpl taskManager, McpServersRuntimeConfig config) {
        this.manager = Objects.requireNonNull(manager);
        this.taskManager = Objects.requireNonNull(taskManager);
        this.config = config;
    }

    Future<Void> toolsList(JsonObject message, McpRequest mcpRequest, JsonObject responseMeta) {
        Object id = Messages.getId(message);
        Cursor cursor = Messages.getCursor(message, mcpRequest.sender());
        if (cursor == null) {
            return Future.succeededFuture();
        }
        LOG.debugf("List tools [id: %s, cursor: %s]", id, cursor);

        McpServerRuntimeConfig serverConfig = config.servers().get(mcpRequest.serverName());
        if (serverConfig == null) {
            throw new IllegalStateException("Server config not found: " + mcpRequest.serverName());
        }
        int pageSize = serverConfig.tools().pageSize();

        JsonArray tools = new JsonArray();
        JsonObject result = new JsonObject().put("tools", tools);
        Page<ToolManager.ToolInfo> page = manager.fetchPage(mcpRequest, cursor, pageSize, message);
        for (ToolManager.ToolInfo info : page) {
            try {
                tools.add(info.asJson());
            } catch (McpException e) {
                return mcpRequest.sender().sendError(id, e.getJsonRpcErrorCode(), e.getMessage());
            } catch (Exception e) {
                LOG.errorf(e, "Unable to encode Tool [%s] as JSON", info.name());
                return mcpRequest.sender().sendInternalError(id);
            }
        }
        if (page.hasNextCursor()) {
            ToolManager.ToolInfo last = page.lastInfo();
            result.put("nextCursor", Cursor.encode(last.createdAt(), cursor.snapshotTimestamp()));
        }
        putCacheControl(result, serverConfig.tools().ttlMs(), serverConfig.tools().cacheScope(),
                mcpRequest.protocolVersion().isStateless());
        return mcpRequest.sender().sendResult(id, result, responseMeta);
    }

    Future<Void> toolsCall(JsonObject message, McpRequest mcpRequest, JsonObject responseMeta) {
        Object id = Messages.getId(message);
        JsonObject params = getParams(message);
        if (params == null) {
            return mcpRequest.sender()
                    .sendError(id, JsonRpcErrorCodes.INVALID_REQUEST, "Missing required params");
        }
        String toolName = params.getString("name");
        LOG.debugf("Call tool %s [id: %s]", toolName, id);

        // A task-augmented tool is executed as a task if the client declared the tasks extension capability
        ToolInfo tool = toolName != null ? manager.getTool(toolName, mcpRequest.serverName()) : null;
        TaskOptions taskOptions = tool != null ? tool.taskOptions().orElse(null) : null;
        if (taskOptions != null && manager.isAvailable(tool, mcpRequest, message)) {
            if (TaskMessageHandler.supportsTasks(mcpRequest)) {
                return toolsCallAsTask(message, mcpRequest, responseMeta, toolName, taskOptions);
            } else if (taskOptions.required()) {
                LOG.debugf("Tool %s requires the tasks extension capability [id: %s]", toolName, id);
                return TaskMessageHandler.sendMissingCapability(id, mcpRequest);
            }
            // Otherwise fall back to the synchronous execution
        }

        try {
            Future<ToolResponse> fu = manager.execute(toolName,
                    new FeatureExecutionContext(message, mcpRequest));
            return fu.compose(toolResponse -> {
                if (toolResponse.isError()) {
                    mcpRequest.setTracingErrorResponse(true, null, null);
                }
                return mcpRequest.sender().sendResult(id, JsonObject.mapFrom(toolResponse), responseMeta);
            },
                    cause -> handleFailure(id, mcpRequest.sender(), mcpRequest, cause, LOG,
                            "Unable to call tool %s", toolName, responseMeta));
        } catch (McpException e) {
            return mcpRequest.sender().sendError(id, e.getJsonRpcErrorCode(), e.getMessage());
        }
    }

    /**
     * Creates a task, immediately responds with a {@code CreateTaskResult} and executes the tool asynchronously on a new
     * duplicated context. The task is updated when the tool completes.
     * <p>
     * The returned future completes when the {@code CreateTaskResult} is sent, i.e. the request context of the original
     * request is terminated while the tool is still running; the tool is therefore executed with its own request context.
     */
    private Future<Void> toolsCallAsTask(JsonObject message, McpRequest mcpRequest, JsonObject responseMeta,
            String toolName, TaskOptions taskOptions) {
        Object id = Messages.getId(message);
        // The task must be durably created before the response is sent
        TaskImpl task = taskManager.create(toolName, taskOptions, mcpRequest.serverName());
        LOG.debugf("Call tool %s as task %s [id: %s]", toolName, task.id(), id);

        JsonObject createTaskResult = task.toJson(false).put("resultType", "task");
        Future<Void> ret = mcpRequest.sender().sendResult(id, createTaskResult, responseMeta);

        // Execute the tool on a new duplicated context
        Context context = VertxContext.createNewDuplicatedContext(manager.vertx.getOrCreateContext());
        VertxContextSafetyToggle.setContextSafe(context, true);
        task.setContext(context);
        context.runOnContext(v -> {
            mcpRequest.contextStart();
            Future<ToolResponse> fu;
            try {
                fu = manager.execute(toolName, new FeatureExecutionContext(message, mcpRequest, null, task));
            } catch (Throwable e) {
                fu = Future.failedFuture(e);
            }
            fu.onComplete(r -> {
                try {
                    if (r.succeeded()) {
                        task.complete(JsonObject.mapFrom(r.result()));
                    } else {
                        handleTaskFailure(task, toolName, r.cause());
                    }
                } finally {
                    mcpRequest.contextEnd(r.cause());
                }
            });
        });
        return ret;
    }

    private void handleTaskFailure(TaskImpl task, String toolName, Throwable cause) {
        if (cause instanceof McpResultException resultException) {
            JsonObject result;
            try {
                result = resultException.result();
                if (result == null) {
                    throw new IllegalStateException(resultException.getClass().getName() + "#result() must not return null");
                }
            } catch (RuntimeException e) {
                LOG.errorf(e, "Unable to obtain the result from %s [%s]", resultException.getClass().getName(), toolName);
                task.fail(JsonRpcErrorCodes.INTERNAL_ERROR, "Internal error", null);
                return;
            }
            task.complete(result.copy());
        } else if (cause instanceof McpException mcp) {
            task.fail(mcp.getJsonRpcErrorCode(), mcp.getMessage(), mcp.getData());
        } else if (cause instanceof Cancellation.OperationCancellationException
                || cause instanceof org.mcpjava.server.Cancellation.OperationCancelledException) {
            LOG.debugf("Operation for task %s was cancelled", task.id());
            // No-op if the task was already cancelled via tasks/cancel
            task.cancel(null);
        } else if (Failures.isSecurityFailure(cause)) {
            task.fail(JsonRpcErrorCodes.SECURITY_ERROR, cause.toString(), null);
        } else {
            LOG.errorf(cause, "Unable to call tool %s as task %s", toolName, task.id());
            task.fail(JsonRpcErrorCodes.INTERNAL_ERROR, "Internal error", null);
        }
    }

}
