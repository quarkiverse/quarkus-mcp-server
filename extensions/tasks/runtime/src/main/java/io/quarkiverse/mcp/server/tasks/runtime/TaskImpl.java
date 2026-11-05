package io.quarkiverse.mcp.server.tasks.runtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.ElicitationRequest;
import io.quarkiverse.mcp.server.InputRequiredException;
import io.quarkiverse.mcp.server.InputRequiredException.ElicitationInputRequest;
import io.quarkiverse.mcp.server.InputRequiredException.InputRequestEntry;
import io.quarkiverse.mcp.server.InputRequiredException.RootsInputRequest;
import io.quarkiverse.mcp.server.InputRequiredException.SamplingInputRequest;
import io.quarkiverse.mcp.server.InputRequiredException.UrlElicitationInputRequest;
import io.quarkiverse.mcp.server.InputResponses;
import io.quarkiverse.mcp.server.SamplingRequest;
import io.quarkiverse.mcp.server.UrlElicitationRequest;
import io.quarkiverse.mcp.server.tasks.TaskContext;
import io.quarkiverse.mcp.server.tasks.TaskInputRequest;
import io.quarkiverse.mcp.server.tasks.TaskStatus;
import io.quarkiverse.mcp.server.tasks.runtime.TaskManagerImpl.Transition;
import io.quarkiverse.mcp.server.tasks.spi.TaskRecord;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;

/**
 * A task executed by this server instance.
 * <p>
 * The persistent state of the task is kept in the {@link io.quarkiverse.mcp.server.tasks.spi.TaskStore}; this class holds the
 * live state that cannot be persisted, i.e. the pending input request and the cancellation actions. It is notified about every
 * new state of the task, either directly (if the transition was applied by this server instance) or when the state is fetched
 * by {@link TaskManagerImpl} at the polling interval of the task (if the transition was applied by another server instance
 * that shares the store).
 * <p>
 * The state transitions initiated by the handler (status message, input request, result) are applied in the order they were
 * initiated. The callbacks (completion of a pending input request, cancellation actions) are always run on the Vert.x context
 * the task is executed on (if available).
 */
public final class TaskImpl implements TaskContext {

    private static final Logger LOG = Logger.getLogger(TaskImpl.class);

    static final String TASK_REMOVED = "Task removed";

    private final TaskManagerImpl manager;
    private final String id;
    private final Cancellation cancellation;
    private final long pollIntervalMillis;

    // The latest known state
    private volatile TaskRecord record;
    // The Vert.x context the tool is executed on
    private volatile Context context;
    // The time (epoch millis) when the state should be checked in the store next time
    private volatile long nextRefreshAt;

    // The following fields are guarded by "this"
    private CompletableFuture<InputResponses> pendingInput;
    private List<String> pendingInputKeys;
    // null if not cancelled
    private Optional<String> cancellationReason;
    private final List<Consumer<Optional<String>>> cancellationActions;
    // The tail of the chain of the state transitions initiated by the handler
    private CompletableFuture<Void> writes;

    TaskImpl(TaskManagerImpl manager, TaskRecord record) {
        this.manager = manager;
        this.id = record.id();
        this.record = record;
        this.cancellation = new TaskCancellation();
        this.pollIntervalMillis = record.pollInterval().toMillis();
        this.nextRefreshAt = System.currentTimeMillis() + pollIntervalMillis;
        this.cancellationActions = new ArrayList<>();
        this.writes = CompletableFuture.completedFuture(null);
    }

    // TaskContext

    @Override
    public String id() {
        return id;
    }

    @Override
    public TaskStatus status() {
        return record.status();
    }

    @Override
    public String statusMessage() {
        return record.statusMessage();
    }

    @Override
    public void setStatusMessage(String statusMessage) {
        enqueue(() -> manager.update(id, r -> TaskManagerImpl.statusMessage(r, statusMessage)))
                .subscribe().with(ignored -> {
                }, t -> LOG.errorf(t, "Unable to set the status message of task %s", id));
    }

    @Override
    public TaskInputRequest.Builder inputRequestBuilder() {
        return new InputRequestBuilder();
    }

    @Override
    public Cancellation cancellation() {
        return cancellation;
    }

    // Internal API

    TaskRecord record() {
        return record;
    }

    void setContext(Context context) {
        this.context = context;
    }

    long nextRefreshAt() {
        return nextRefreshAt;
    }

    /**
     * Schedules the next check of the state in the store, i.e. at the polling interval of the task.
     *
     * @param now the current time (epoch millis)
     */
    void scheduleNextRefresh(long now) {
        nextRefreshAt = now + pollIntervalMillis;
    }

    Uni<Transition> complete(JsonObject result) {
        return enqueue(() -> manager.update(id, r -> TaskManagerImpl.completed(r, result)));
    }

    Uni<Transition> fail(int code, String message, Object data) {
        return enqueue(() -> manager.update(id, r -> TaskManagerImpl.failed(r, code, message, data)));
    }

    Uni<Transition> cancel(String reason) {
        return enqueue(() -> manager.update(id, r -> TaskManagerImpl.cancelled(r, reason)));
    }

    /**
     * Invoked when a new state of the task is available.
     */
    void onUpdate(TaskRecord updated) {
        CompletableFuture<InputResponses> toComplete = null;
        InputResponses responses = null;
        synchronized (this) {
            if (updated.version() <= record.version()) {
                // Stale state
                return;
            }
            record = updated;
            if (pendingInput != null && updated.status() != TaskStatus.INPUT_REQUIRED) {
                // The accessor returns a new copy
                JsonObject inputResponses = updated.inputResponses();
                if (inputResponses != null && pendingInputKeys.stream().allMatch(inputResponses::containsKey)) {
                    toComplete = pendingInput;
                    responses = new TaskInputResponses(inputResponses);
                    pendingInput = null;
                    pendingInputKeys = null;
                }
            }
        }
        if (updated.status() == TaskStatus.CANCELLED) {
            onCancelled(Optional.ofNullable(updated.cancellationReason()));
        }
        if (toComplete != null) {
            CompletableFuture<InputResponses> future = toComplete;
            InputResponses value = responses;
            runOnContext(() -> future.complete(value));
        }
        if (updated.status().isTerminal()) {
            manager.untrack(this);
        }
    }

    /**
     * Invoked when the task was removed from the store.
     */
    void onRemoved(String reason) {
        onCancelled(Optional.ofNullable(reason));
        manager.untrack(this);
    }

    /**
     * Invoked when the state of the task was fetched from the store, i.e. when a transition might have been applied by another
     * server instance.
     *
     * @param fetched the state fetched from the store, or {@code null} if the task does not exist anymore
     */
    void onRefresh(TaskRecord fetched) {
        if (fetched == null) {
            LOG.debugf("Task %s was removed from the store", id);
            onRemoved(record.isExpired(Instant.now()) ? TaskManagerImpl.TASK_EXPIRED : TASK_REMOVED);
        } else {
            onUpdate(fetched);
        }
    }

    /**
     * A pending input request is failed and the registered cancellation actions are executed; no-op if already cancelled.
     */
    private void onCancelled(Optional<String> reason) {
        CompletableFuture<InputResponses> pending;
        List<Consumer<Optional<String>>> actions;
        synchronized (this) {
            if (cancellationReason != null) {
                return;
            }
            cancellationReason = reason;
            pending = pendingInput;
            pendingInput = null;
            pendingInputKeys = null;
            actions = List.copyOf(cancellationActions);
            cancellationActions.clear();
        }
        LOG.debugf("Task %s cancelled", id);
        runOnContext(() -> {
            if (pending != null) {
                pending.completeExceptionally(new Cancellation.OperationCancellationException());
            }
            for (Consumer<Optional<String>> action : actions) {
                try {
                    action.accept(reason);
                } catch (Exception e) {
                    LOG.errorf(e, "Error executing cancellation action of task %s", id);
                }
            }
        });
    }

    Uni<InputResponses> requestInput(Map<String, InputRequestEntry> inputRequests) {
        List<String> keys = List.copyOf(inputRequests.keySet());
        JsonObject inputRequestsJson = toInputRequestsJson(inputRequests);
        CompletableFuture<InputResponses> future = new CompletableFuture<>();
        synchronized (this) {
            if (cancellationReason != null) {
                return Uni.createFrom().failure(new Cancellation.OperationCancellationException());
            }
            if (pendingInput != null) {
                return Uni.createFrom().failure(
                        new IllegalStateException("Task " + id + " already has a pending input request"));
            }
            // Register the pending input before the transition is applied so that no update is missed
            pendingInput = future;
            pendingInputKeys = keys;
        }
        return enqueue(() -> manager.update(id, r -> inputRequired(r, keys, inputRequestsJson)))
                .onFailure().invoke(t -> clearPendingInput(future))
                .flatMap(transition -> {
                    if (transition.current() == null) {
                        clearPendingInput(future);
                        return Uni.createFrom().failure(new IllegalStateException("Task " + id + " does not exist"));
                    }
                    return Uni.createFrom().completionStage(future);
                });
    }

    private static TaskRecord.Builder inputRequired(TaskRecord r, List<String> keys, JsonObject inputRequestsJson) {
        if (r.status() == TaskStatus.CANCELLED) {
            throw new Cancellation.OperationCancellationException();
        }
        if (r.status().isTerminal()) {
            throw new IllegalStateException("Task " + r.id() + " is already " + r.status().jsonValue());
        }
        if (r.status() == TaskStatus.INPUT_REQUIRED) {
            throw new IllegalStateException("Task " + r.id() + " already has a pending input request");
        }
        for (String key : keys) {
            if (r.hasInputRequest(key)) {
                throw new IllegalArgumentException(
                        "Input request key [" + key + "] was already used during the lifetime of task " + r.id());
            }
        }
        return r.toBuilder()
                .status(TaskStatus.INPUT_REQUIRED)
                .addInputRequests(inputRequestsJson)
                .outstandingInputRequestKeys(keys)
                .inputResponses(new JsonObject());
    }

    private synchronized void clearPendingInput(CompletableFuture<InputResponses> future) {
        if (pendingInput == future) {
            pendingInput = null;
            pendingInputKeys = null;
        }
    }

    /**
     * Applies the given state transition once all previously initiated transitions are applied.
     */
    private <T> Uni<T> enqueue(Supplier<Uni<T>> transition) {
        CompletableFuture<T> result = new CompletableFuture<>();
        synchronized (this) {
            writes = writes.thenCompose(ignored -> {
                Uni<T> uni;
                try {
                    uni = transition.get();
                } catch (Throwable t) {
                    result.completeExceptionally(t);
                    return CompletableFuture.completedFuture(null);
                }
                return uni.subscribeAsCompletionStage().handle((r, t) -> {
                    if (t != null) {
                        result.completeExceptionally(t);
                    } else {
                        result.complete(r);
                    }
                    return null;
                });
            });
        }
        return Uni.createFrom().completionStage(result);
    }

    /**
     * Serializes the input requests with the public {@link InputRequiredException} API, i.e. in the same shape as the
     * {@code inputRequests} of a Multi Round-Trip Request.
     */
    private static JsonObject toInputRequestsJson(Map<String, InputRequestEntry> inputRequests) {
        InputRequiredException.Builder builder = InputRequiredException.builder();
        for (Map.Entry<String, InputRequestEntry> e : inputRequests.entrySet()) {
            InputRequestEntry entry = e.getValue();
            if (entry instanceof ElicitationInputRequest elicitation) {
                builder.addElicitationRequest(e.getKey(), elicitation.request());
            } else if (entry instanceof UrlElicitationInputRequest urlElicitation) {
                builder.addUrlElicitationRequest(e.getKey(), urlElicitation.request());
            } else if (entry instanceof SamplingInputRequest sampling) {
                builder.addSamplingRequest(e.getKey(), sampling.request());
            } else {
                builder.addRootsRequest(e.getKey());
            }
        }
        return builder.build().result().getJsonObject("inputRequests");
    }

    private void runOnContext(Runnable runnable) {
        Context ctx = context;
        if (ctx != null) {
            ctx.runOnContext(v -> runnable.run());
        } else {
            runnable.run();
        }
    }

    @Override
    public String toString() {
        TaskRecord r = record;
        return "Task [id=" + id + ", tool=" + r.toolName() + ", server=" + r.serverName() + ", status=" + r.status() + "]";
    }

    final class TaskCancellation implements Cancellation {

        @Override
        public Result check() {
            Optional<String> reason;
            synchronized (TaskImpl.this) {
                reason = cancellationReason;
            }
            if (reason == null) {
                return new Result(false, Optional.empty());
            }
            return new Result(true, reason);
        }

        @Override
        public void onCancelled(Consumer<Optional<String>> action) {
            Objects.requireNonNull(action, "action must not be null");
            Optional<String> reason;
            synchronized (TaskImpl.this) {
                reason = cancellationReason;
                if (reason == null) {
                    cancellationActions.add(action);
                    return;
                }
            }
            // Already cancelled - run the action immediately
            action.accept(reason);
        }

    }

    final class InputRequestBuilder implements TaskInputRequest.Builder {

        private final Map<String, InputRequestEntry> inputRequests = new LinkedHashMap<>();

        @Override
        public TaskInputRequest.Builder addElicitationRequest(String key, ElicitationRequest request) {
            inputRequests.put(Objects.requireNonNull(key), new ElicitationInputRequest(request));
            return this;
        }

        @Override
        public TaskInputRequest.Builder addUrlElicitationRequest(String key, UrlElicitationRequest request) {
            inputRequests.put(Objects.requireNonNull(key), new UrlElicitationInputRequest(request));
            return this;
        }

        @Override
        public TaskInputRequest.Builder addSamplingRequest(String key, SamplingRequest request) {
            inputRequests.put(Objects.requireNonNull(key), new SamplingInputRequest(request));
            return this;
        }

        @Override
        public TaskInputRequest.Builder addRootsRequest(String key) {
            inputRequests.put(Objects.requireNonNull(key), new RootsInputRequest());
            return this;
        }

        @Override
        public TaskInputRequest build() {
            if (inputRequests.isEmpty()) {
                throw new IllegalStateException("At least one input request must be added");
            }
            return new TaskInputRequestImpl(Map.copyOf(inputRequests), new LinkedHashMap<>(inputRequests));
        }

    }

    final class TaskInputRequestImpl implements TaskInputRequest {

        private final Map<String, InputRequestEntry> inputRequests;
        private final Map<String, InputRequestEntry> ordered;

        TaskInputRequestImpl(Map<String, InputRequestEntry> inputRequests, Map<String, InputRequestEntry> ordered) {
            this.inputRequests = inputRequests;
            this.ordered = ordered;
        }

        @Override
        public Map<String, InputRequestEntry> inputRequests() {
            return inputRequests;
        }

        @Override
        public Uni<InputResponses> send() {
            return requestInput(ordered);
        }

    }

}
