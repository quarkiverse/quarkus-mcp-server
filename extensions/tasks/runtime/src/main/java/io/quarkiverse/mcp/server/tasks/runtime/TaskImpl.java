package io.quarkiverse.mcp.server.tasks.runtime;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.ElicitationRequest;
import io.quarkiverse.mcp.server.InputRequiredException.ElicitationInputRequest;
import io.quarkiverse.mcp.server.InputRequiredException.InputRequestEntry;
import io.quarkiverse.mcp.server.InputRequiredException.RootsInputRequest;
import io.quarkiverse.mcp.server.InputRequiredException.SamplingInputRequest;
import io.quarkiverse.mcp.server.InputRequiredException.UrlElicitationInputRequest;
import io.quarkiverse.mcp.server.InputResponses;
import io.quarkiverse.mcp.server.SamplingRequest;
import io.quarkiverse.mcp.server.UrlElicitationRequest;
import io.quarkiverse.mcp.server.runtime.InputRequestSupport;
import io.quarkiverse.mcp.server.runtime.InputResponsesImpl;
import io.quarkiverse.mcp.server.tasks.TaskContext;
import io.quarkiverse.mcp.server.tasks.TaskInputRequest;
import io.quarkiverse.mcp.server.tasks.TaskManager;
import io.quarkiverse.mcp.server.tasks.TaskStatus;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;

/**
 * The state machine of a task created for a task-augmented tool.
 * <p>
 * All state transitions are guarded by the monitor of this instance; the callbacks (completion of a pending input request,
 * cancellation actions) are always run outside the monitor, on the Vert.x context the task is executed on (if available).
 */
public final class TaskImpl implements TaskContext, TaskManager.TaskInfo {

    private static final Logger LOG = Logger.getLogger(TaskImpl.class);

    private final String id;
    private final String serverName;
    private final String toolName;
    private final Instant createdAt;
    // null means unlimited
    private final Duration ttl;
    private final Duration pollInterval;
    private final TaskManagerImpl manager;
    private final Cancellation cancellation;

    // The Vert.x context the tool is executed on
    private volatile Context context;

    // The following fields are guarded by "this"
    private TaskStatus status;
    private String statusMessage;
    private Instant lastUpdatedAt;
    private JsonObject result;
    private JsonObject error;
    private final Map<String, InputRequestEntry> outstandingInputRequests;
    private final Set<String> usedInputRequestKeys;
    private JsonObject collectedInputResponses;
    private CompletableFuture<InputResponses> pendingInput;
    // null if cancellation was not requested
    private Optional<String> cancellationReason;
    private final List<Consumer<Optional<String>>> cancellationActions;

    TaskImpl(String id, String serverName, String toolName, Duration ttl, Duration pollInterval, TaskManagerImpl manager) {
        this.id = Objects.requireNonNull(id);
        this.serverName = Objects.requireNonNull(serverName);
        this.toolName = Objects.requireNonNull(toolName);
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        this.ttl = ttl;
        this.pollInterval = Objects.requireNonNull(pollInterval);
        this.manager = manager;
        this.cancellation = new TaskCancellation();
        this.status = TaskStatus.WORKING;
        this.lastUpdatedAt = createdAt;
        this.outstandingInputRequests = new LinkedHashMap<>();
        this.usedInputRequestKeys = new HashSet<>();
        this.cancellationActions = new ArrayList<>();
    }

    // TaskInfo

    @Override
    public String id() {
        return id;
    }

    @Override
    public String serverName() {
        return serverName;
    }

    @Override
    public String toolName() {
        return toolName;
    }

    @Override
    public synchronized TaskStatus status() {
        return status;
    }

    @Override
    public synchronized String statusMessage() {
        return statusMessage;
    }

    @Override
    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public synchronized Instant lastUpdatedAt() {
        return lastUpdatedAt;
    }

    @Override
    public Optional<Duration> ttl() {
        return Optional.ofNullable(ttl);
    }

    @Override
    public Duration pollInterval() {
        return pollInterval;
    }

    // TaskContext

    @Override
    public boolean isTaskAugmented() {
        return true;
    }

    @Override
    public void setStatusMessage(String statusMessage) {
        synchronized (this) {
            this.statusMessage = statusMessage;
            touch();
        }
        manager.taskChanged(this);
    }

    @Override
    public TaskInputRequest.Builder inputRequestBuilder() {
        return new InputRequestBuilder();
    }

    // Internal API

    void setContext(Context context) {
        this.context = context;
    }

    Cancellation cancellation() {
        return cancellation;
    }

    boolean isExpired() {
        return ttl != null && Instant.now().isAfter(createdAt.plus(ttl));
    }

    /**
     * @return {@code true} if the status was changed, {@code false} if the task is already in a terminal status
     */
    boolean complete(JsonObject result) {
        synchronized (this) {
            if (status.isTerminal()) {
                LOG.debugf("Task %s is already %s - the result is discarded", id, status.jsonValue());
                return false;
            }
            this.status = TaskStatus.COMPLETED;
            this.result = result;
            touch();
        }
        manager.taskChanged(this);
        return true;
    }

    /**
     * @return {@code true} if the status was changed, {@code false} if the task is already in a terminal status
     */
    boolean fail(int code, String message, Object data) {
        synchronized (this) {
            if (status.isTerminal()) {
                LOG.debugf("Task %s is already %s - the error is discarded: %s", id, status.jsonValue(), message);
                return false;
            }
            this.status = TaskStatus.FAILED;
            JsonObject err = new JsonObject().put("code", code).put("message", message);
            if (data != null) {
                err.put("data", data);
            }
            this.error = err;
            if (this.statusMessage == null) {
                this.statusMessage = message;
            }
            touch();
        }
        manager.taskChanged(this);
        return true;
    }

    /**
     * Cancels the task. The task moves to the {@code cancelled} status immediately, a pending input request is failed and the
     * registered cancellation actions are executed.
     *
     * @return {@code true} if the task was cancelled, {@code false} if it is already in a terminal status
     */
    boolean cancel(String reason) {
        CompletableFuture<InputResponses> pending;
        List<Consumer<Optional<String>>> actions;
        Optional<String> optReason = Optional.ofNullable(reason);
        synchronized (this) {
            if (status.isTerminal()) {
                return false;
            }
            this.status = TaskStatus.CANCELLED;
            if (reason != null) {
                this.statusMessage = reason;
            }
            this.cancellationReason = optReason;
            pending = pendingInput;
            pendingInput = null;
            outstandingInputRequests.clear();
            collectedInputResponses = null;
            actions = List.copyOf(cancellationActions);
            cancellationActions.clear();
            touch();
        }
        runOnContext(() -> {
            if (pending != null) {
                pending.completeExceptionally(new Cancellation.OperationCancellationException());
            }
            for (Consumer<Optional<String>> action : actions) {
                try {
                    action.accept(optReason);
                } catch (Exception e) {
                    LOG.errorf(e, "Error executing cancellation action of task %s", id);
                }
            }
        });
        manager.taskChanged(this);
        return true;
    }

    Uni<InputResponses> requestInput(Map<String, InputRequestEntry> inputRequests) {
        CompletableFuture<InputResponses> future = new CompletableFuture<>();
        synchronized (this) {
            if (status == TaskStatus.CANCELLED) {
                return Uni.createFrom().failure(new Cancellation.OperationCancellationException());
            }
            if (status.isTerminal()) {
                return Uni.createFrom().failure(
                        new IllegalStateException("Task " + id + " is already " + status.jsonValue()));
            }
            if (pendingInput != null) {
                return Uni.createFrom().failure(
                        new IllegalStateException("Task " + id + " already has a pending input request"));
            }
            for (String key : inputRequests.keySet()) {
                if (usedInputRequestKeys.contains(key)) {
                    return Uni.createFrom().failure(new IllegalArgumentException(
                            "Input request key [" + key + "] was already used during the lifetime of task " + id));
                }
            }
            usedInputRequestKeys.addAll(inputRequests.keySet());
            outstandingInputRequests.putAll(inputRequests);
            collectedInputResponses = new JsonObject();
            pendingInput = future;
            status = TaskStatus.INPUT_REQUIRED;
            touch();
        }
        manager.taskChanged(this);
        return Uni.createFrom().completionStage(future);
    }

    /**
     * Processes the {@code inputResponses} of a {@code tasks/update} request. Responses for keys that are not currently
     * outstanding are ignored. Once all outstanding requests are fulfilled the task moves back to {@code working}.
     */
    void updateInputResponses(JsonObject inputResponses) {
        CompletableFuture<InputResponses> toComplete = null;
        InputResponses responses = null;
        synchronized (this) {
            if (status != TaskStatus.INPUT_REQUIRED || pendingInput == null) {
                LOG.debugf("Task %s is not waiting for input - the input responses are ignored", id);
                return;
            }
            for (String key : inputResponses.fieldNames()) {
                if (outstandingInputRequests.remove(key) != null) {
                    collectedInputResponses.put(key, inputResponses.getValue(key));
                } else {
                    LOG.debugf("Task %s: ignored input response for unknown or already satisfied key: %s", id, key);
                }
            }
            if (outstandingInputRequests.isEmpty()) {
                toComplete = pendingInput;
                responses = InputResponsesImpl.of(collectedInputResponses);
                pendingInput = null;
                collectedInputResponses = null;
                status = TaskStatus.WORKING;
            }
            touch();
        }
        if (toComplete != null) {
            CompletableFuture<InputResponses> future = toComplete;
            InputResponses value = responses;
            runOnContext(() -> future.complete(value));
            manager.taskChanged(this);
        }
    }

    /**
     * @param detailed if {@code true} then the status-specific fields ({@code inputRequests}, {@code result}, {@code error})
     *        are included, i.e. a {@code DetailedTask} is produced
     * @return a new JSON representation of the task
     */
    synchronized JsonObject toJson(boolean detailed) {
        JsonObject json = new JsonObject()
                .put("taskId", id)
                .put("status", status.jsonValue());
        if (statusMessage != null) {
            json.put("statusMessage", statusMessage);
        }
        json.put("createdAt", DateTimeFormatter.ISO_INSTANT.format(createdAt));
        json.put("lastUpdatedAt", DateTimeFormatter.ISO_INSTANT.format(lastUpdatedAt));
        if (ttl != null) {
            json.put("ttlMs", ttl.toMillis());
        } else {
            json.putNull("ttlMs");
        }
        json.put("pollIntervalMs", pollInterval.toMillis());
        if (detailed) {
            switch (status) {
                case INPUT_REQUIRED -> {
                    JsonObject inputRequests = new JsonObject();
                    for (Map.Entry<String, InputRequestEntry> e : outstandingInputRequests.entrySet()) {
                        inputRequests.put(e.getKey(), InputRequestSupport.toInputRequestJson(e.getValue()));
                    }
                    json.put("inputRequests", inputRequests);
                }
                case COMPLETED -> json.put("result", result != null ? result.copy() : new JsonObject());
                case FAILED -> json.put("error", error != null ? error.copy() : new JsonObject());
                default -> {
                    // no status-specific fields
                }
            }
        }
        return json;
    }

    private void touch() {
        lastUpdatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
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
        return "Task [id=" + id + ", tool=" + toolName + ", server=" + serverName + ", status=" + status() + "]";
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

    /**
     * The context of a task-augmented tool that is executed synchronously, i.e. not as a task.
     */
    public static final class NoTaskContext implements TaskContext {

        public static final NoTaskContext INSTANCE = new NoTaskContext();

        private NoTaskContext() {
        }

        @Override
        public boolean isTaskAugmented() {
            return false;
        }

        @Override
        public String id() {
            return null;
        }

        @Override
        public TaskStatus status() {
            return null;
        }

        @Override
        public String statusMessage() {
            return null;
        }

        @Override
        public void setStatusMessage(String statusMessage) {
            // no-op
        }

        @Override
        public TaskInputRequest.Builder inputRequestBuilder() {
            throw new IllegalStateException(
                    "The tool is not executed as a task; the client did not declare the tasks extension capability");
        }

    }

}
