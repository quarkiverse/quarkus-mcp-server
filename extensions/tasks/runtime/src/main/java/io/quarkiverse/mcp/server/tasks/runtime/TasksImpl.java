package io.quarkiverse.mcp.server.tasks.runtime;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.ExecutionModel;
import io.quarkiverse.mcp.server.FeatureManager.RequestFeatureArguments;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpResultException;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.runtime.Messages;
import io.quarkiverse.mcp.server.tasks.CreateTaskException;
import io.quarkiverse.mcp.server.tasks.TaskContext;
import io.quarkiverse.mcp.server.tasks.Tasks;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.security.AuthenticationException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.quarkus.virtual.threads.VirtualThreadsRecorder;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * The {@link Tasks} object injected into a tool method; bound to the current request.
 */
final class TasksImpl implements Tasks {

    private static final Logger LOG = Logger.getLogger(TasksImpl.class);

    private final TaskManagerImpl manager;
    private final Vertx vertx;
    private final CurrentIdentityAssociation identityAssociation;
    private final RequestFeatureArguments arguments;
    private final SecurityIdentity identity;
    private final boolean supported;

    TasksImpl(TaskManagerImpl manager, Vertx vertx, CurrentIdentityAssociation identityAssociation,
            RequestFeatureArguments arguments, SecurityIdentity identity) {
        this.manager = manager;
        this.vertx = vertx;
        this.identityAssociation = identityAssociation;
        this.arguments = arguments;
        this.identity = identity;
        this.supported = TasksExtension.supportsTasks(arguments.connection());
    }

    @Override
    public boolean isSupported() {
        return supported;
    }

    @Override
    public TaskDefinition newTask() {
        if (!supported) {
            throw TasksExtension.missingCapability();
        }
        return new TaskDefinitionImpl();
    }

    private String toolName() {
        JsonObject params = Messages.getParams(arguments.rawMessage().asJsonObject());
        String name = params != null ? params.getString("name") : null;
        return name != null ? name : "<unknown>";
    }

    private final class TaskDefinitionImpl implements TaskDefinition {

        private Duration ttl;
        private Duration pollInterval;
        private String statusMessage;
        private Function<TaskContext, ToolResponse> handler;
        private boolean runOnVirtualThread;
        private Function<TaskContext, Uni<ToolResponse>> asyncHandler;

        @Override
        public TaskDefinition setTtl(Duration ttl) {
            this.ttl = Objects.requireNonNull(ttl);
            return this;
        }

        @Override
        public TaskDefinition setPollInterval(Duration pollInterval) {
            Objects.requireNonNull(pollInterval);
            if (pollInterval.isZero() || pollInterval.isNegative()) {
                throw new IllegalArgumentException("pollInterval must be positive");
            }
            this.pollInterval = pollInterval;
            return this;
        }

        @Override
        public TaskDefinition setStatusMessage(String statusMessage) {
            this.statusMessage = statusMessage;
            return this;
        }

        @Override
        public TaskDefinition setHandler(Function<TaskContext, ToolResponse> handler, boolean runOnVirtualThread) {
            this.handler = Objects.requireNonNull(handler);
            this.runOnVirtualThread = runOnVirtualThread;
            this.asyncHandler = null;
            return this;
        }

        @Override
        public TaskDefinition setAsyncHandler(Function<TaskContext, Uni<ToolResponse>> handler) {
            this.asyncHandler = Objects.requireNonNull(handler);
            this.handler = null;
            return this;
        }

        @Override
        public CreateTaskException create() {
            if (handler == null && asyncHandler == null) {
                throw new IllegalStateException("Either a blocking or an async handler must be set");
            }
            ExecutionModel executionModel;
            Function<TaskContext, Uni<ToolResponse>> action;
            if (handler != null) {
                executionModel = runOnVirtualThread ? ExecutionModel.VIRTUAL_THREAD : ExecutionModel.WORKER_THREAD;
                // The handler is invoked eagerly, i.e. on the thread selected by the execution model
                action = task -> Uni.createFrom().item(handler.apply(task));
            } else {
                executionModel = ExecutionModel.EVENT_LOOP;
                action = asyncHandler;
            }
            // The task must be durably created before the CreateTaskResult is sent
            TaskImpl task = manager.create(toolName(), arguments.connection().serverName(), ttl, pollInterval,
                    statusMessage);
            execute(task, executionModel, action);
            return new CreateTaskException(task, task.toJson(false).put("resultType", "task"));
        }

    }

    /**
     * Executes the handler on a new duplicated Vert.x context, with its own request context, according to the execution
     * model; the task is updated once the handler completes.
     */
    private void execute(TaskImpl task, ExecutionModel executionModel, Function<TaskContext, Uni<ToolResponse>> action) {
        Context context = VertxContext.createNewDuplicatedContext(vertx.getOrCreateContext());
        VertxContextSafetyToggle.setContextSafe(context, true);
        task.setContext(context);
        LOG.debugf("Execute task %s [%s]", task.id(), executionModel);
        context.runOnContext(v -> {
            ManagedContext requestContext = Arc.container().requestContext();
            requestContext.activate();
            if (identity != null && identityAssociation != null) {
                identityAssociation.setIdentity(identity);
            }
            Runnable run = () -> {
                try {
                    action.apply(task).subscribe().with(
                            r -> finish(task, context, requestContext, r, null),
                            t -> finish(task, context, requestContext, null, t));
                } catch (Throwable t) {
                    finish(task, context, requestContext, null, t);
                }
            };
            switch (executionModel) {
                case VIRTUAL_THREAD -> VirtualThreadsRecorder.getCurrent().execute(run);
                case WORKER_THREAD -> vertx.executeBlocking(() -> {
                    run.run();
                    return null;
                }, false);
                default -> run.run();
            }
        });
    }

    private void finish(TaskImpl task, Context context, ManagedContext requestContext, ToolResponse response,
            Throwable failure) {
        // Always finish on the task context so that the request context is terminated where it was activated
        context.runOnContext(v -> {
            try {
                if (failure == null) {
                    task.complete(JsonObject.mapFrom(response));
                } else {
                    handleFailure(task, failure);
                }
            } finally {
                requestContext.terminate();
            }
        });
    }

    private void handleFailure(TaskImpl task, Throwable cause) {
        if (cause instanceof ToolCallException) {
            // Business logic error should result in ToolResponse with isError:true
            task.complete(JsonObject.mapFrom(ToolResponse.error(cause.getMessage())));
        } else if (cause instanceof McpResultException resultException) {
            JsonObject result;
            try {
                result = Objects.requireNonNull(resultException.result(), "result() must not return null");
            } catch (RuntimeException e) {
                LOG.errorf(e, "Unable to obtain the result from %s [task: %s]", resultException.getClass().getName(),
                        task.id());
                task.fail(JsonRpcErrorCodes.INTERNAL_ERROR, "Internal error", null);
                return;
            }
            task.complete(result.copy());
        } else if (cause instanceof McpException mcp) {
            task.fail(mcp.getJsonRpcErrorCode(), mcp.getMessage(), mcp.getData());
        } else if (cause instanceof Cancellation.OperationCancellationException
                || cause instanceof org.mcpjava.server.Cancellation.OperationCancelledException) {
            LOG.debugf("Operation of task %s was cancelled", task.id());
            // No-op if the task was already cancelled via tasks/cancel
            task.cancel(null);
        } else if (cause instanceof UnauthorizedException
                || cause instanceof AuthenticationException
                || cause instanceof ForbiddenException) {
            task.fail(JsonRpcErrorCodes.SECURITY_ERROR, cause.toString(), null);
        } else {
            LOG.errorf(cause, "Unable to execute task %s", task.id());
            task.fail(JsonRpcErrorCodes.INTERNAL_ERROR, "Internal error", null);
        }
    }

}
