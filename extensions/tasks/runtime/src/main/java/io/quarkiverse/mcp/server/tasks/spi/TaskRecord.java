package io.quarkiverse.mcp.server.tasks.spi;

import static io.quarkiverse.mcp.server.tasks.spi.TaskRecordJson.copy;
import static io.quarkiverse.mcp.server.tasks.spi.TaskRecordJson.merge;
import static io.quarkiverse.mcp.server.tasks.spi.TaskRecordJson.normalize;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.quarkiverse.mcp.server.tasks.TaskManager;
import io.quarkiverse.mcp.server.tasks.TaskStatus;
import io.vertx.core.json.JsonObject;

/**
 * An immutable snapshot of the persistent state of a task, as kept in a {@link TaskStore}.
 * <p>
 * Every successful state transition produces a new record with an incremented {@link #version()}; the version is used by
 * {@link TaskStore#replace(TaskRecord, TaskRecord)} to detect concurrent modifications.
 * <p>
 * A record is immutable: the JSON values ({@link #result()}, {@link #error()}, {@link #inputRequests()},
 * {@link #inputResponses()}) are normalized when passed to the {@link Builder}, i.e. converted to a new object that contains
 * JSON types only, and the accessors return a new copy. A value that already contains JSON types only is deep-copied;
 * arbitrary values (POJOs, Jackson nodes, etc.) are encoded and decoded.
 * <p>
 * A {@link TaskStore} may persist a record in any form. For example, it can map the individual values to table columns (or
 * hash fields, entity attributes, etc.) via the accessors and reconstruct the record via {@link #builder()}. Only the
 * payloads ({@link #result()}, {@link #error()}, {@link #inputRequests()}, {@link #inputResponses()}) are JSON by nature, i.e.
 * MCP messages, and are typically stored as JSON text. Optionally, the whole record can be converted to/from a single JSON
 * document via {@link #toJson()} and {@link #fromJson(JsonObject)}; this is merely a convenience for document-oriented stores.
 *
 * @see TaskStore
 */
public final class TaskRecord implements TaskManager.TaskInfo {

    private final String id;
    private final String serverName;
    private final String toolName;
    private final String executionNodeId;
    private final long version;
    private final TaskStatus status;
    private final String statusMessage;
    private final Instant createdAt;
    private final Instant lastUpdatedAt;
    private final Duration ttl;
    private final Duration pollInterval;
    private final JsonObject result;
    private final JsonObject error;
    private final JsonObject inputRequests;
    private final List<String> outstandingInputRequestKeys;
    private final JsonObject inputResponses;
    private final String cancellationReason;

    private TaskRecord(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.serverName = Objects.requireNonNull(builder.serverName, "serverName must not be null");
        this.toolName = Objects.requireNonNull(builder.toolName, "toolName must not be null");
        this.executionNodeId = Objects.requireNonNull(builder.executionNodeId, "executionNodeId must not be null");
        this.version = builder.version;
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.statusMessage = builder.statusMessage;
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.lastUpdatedAt = builder.lastUpdatedAt != null ? builder.lastUpdatedAt : builder.createdAt;
        this.ttl = builder.ttl;
        this.pollInterval = Objects.requireNonNull(builder.pollInterval, "pollInterval must not be null");
        this.result = builder.result;
        this.error = builder.error;
        this.inputRequests = builder.inputRequests != null ? builder.inputRequests : new JsonObject();
        this.outstandingInputRequestKeys = builder.outstandingInputRequestKeys != null
                ? List.copyOf(builder.outstandingInputRequestKeys)
                : List.of();
        this.inputResponses = builder.inputResponses;
        this.cancellationReason = builder.cancellationReason;
    }

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
    public TaskStatus status() {
        return status;
    }

    @Override
    public String statusMessage() {
        return statusMessage;
    }

    @Override
    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public Instant lastUpdatedAt() {
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

    /**
     * The identifier of the server instance (node) that executes the task handler and holds the live state of the task. A new
     * identifier is generated at each start of a server instance, i.e. a task whose execution node is not running anymore will
     * not progress until it expires.
     *
     * @return the identifier of the execution node; never {@code null}
     */
    public String executionNodeId() {
        return executionNodeId;
    }

    /**
     * @return the version of this record; incremented with every state transition
     */
    public long version() {
        return version;
    }

    /**
     * @return a copy of the {@code CallToolResult} if {@link TaskStatus#COMPLETED}, or {@code null}
     */
    public JsonObject result() {
        return copy(result);
    }

    /**
     * @return a copy of the JSON-RPC error if {@link TaskStatus#FAILED}, or {@code null}
     */
    public JsonObject error() {
        return copy(error);
    }

    /**
     * @return a copy of all the input requests sent during the lifetime of the task, keyed by the request key; never
     *         {@code null}
     */
    public JsonObject inputRequests() {
        return copy(inputRequests);
    }

    /**
     * @param key the request key
     * @return a copy of the input request with the given key, or {@code null}
     */
    public JsonObject inputRequest(String key) {
        return copy(inputRequests.getJsonObject(key));
    }

    /**
     * @param key the request key
     * @return {@code true} if an input request with the given key was sent during the lifetime of the task
     */
    public boolean hasInputRequest(String key) {
        return inputRequests.containsKey(key);
    }

    /**
     * @return the keys of the input requests that were not fulfilled yet; never {@code null}
     */
    public List<String> outstandingInputRequestKeys() {
        return outstandingInputRequestKeys;
    }

    /**
     * @return a copy of the input responses collected for the last input request, or {@code null}
     */
    public JsonObject inputResponses() {
        return copy(inputResponses);
    }

    /**
     * @return the reason of the cancellation, or {@code null}
     */
    public String cancellationReason() {
        return cancellationReason;
    }

    /**
     * @param now the current time
     * @return {@code true} if the time-to-live elapsed
     */
    public boolean isExpired(Instant now) {
        return ttl != null && now.isAfter(createdAt.plus(ttl));
    }

    /**
     * @return the expiration time, or {@code null} if the time-to-live is unlimited
     */
    public Instant expiresAt() {
        return ttl != null ? createdAt.plus(ttl) : null;
    }

    /**
     * @return a new builder initialized with the state of this record
     */
    public Builder toBuilder() {
        // The JSON values are owned by this record and never modified, so they can be shared with the builder
        Builder builder = new Builder();
        builder.id = id;
        builder.serverName = serverName;
        builder.toolName = toolName;
        builder.executionNodeId = executionNodeId;
        builder.version = version;
        builder.status = status;
        builder.statusMessage = statusMessage;
        builder.createdAt = createdAt;
        builder.lastUpdatedAt = lastUpdatedAt;
        builder.ttl = ttl;
        builder.pollInterval = pollInterval;
        builder.result = result;
        builder.error = error;
        builder.inputRequests = inputRequests;
        builder.outstandingInputRequestKeys = outstandingInputRequestKeys;
        builder.inputResponses = inputResponses;
        builder.cancellationReason = cancellationReason;
        return builder;
    }

    /**
     * Converts this record to a single JSON document. This is an optional convenience for document-oriented stores; a
     * {@link TaskStore} is not required to use it.
     *
     * @return a new JSON representation of this record
     * @see #fromJson(JsonObject)
     */
    public JsonObject toJson() {
        return TaskRecordJson.toJson(this);
    }

    /**
     * Reconstructs a record from the JSON document produced by {@link #toJson()}. This is an optional convenience for
     * document-oriented stores; a {@link TaskStore} is not required to use it.
     *
     * @param json the JSON representation produced by {@link #toJson()}
     * @return a new record
     */
    public static TaskRecord fromJson(JsonObject json) {
        return TaskRecordJson.fromJson(json);
    }

    /**
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "TaskRecord [id=" + id + ", tool=" + toolName + ", server=" + serverName + ", status=" + status
                + ", version=" + version + "]";
    }

    /**
     * Builder of {@link TaskRecord}.
     */
    public static final class Builder {

        private String id;
        private String serverName;
        private String toolName;
        private String executionNodeId;
        private long version;
        private TaskStatus status;
        private String statusMessage;
        private Instant createdAt;
        private Instant lastUpdatedAt;
        private Duration ttl;
        private Duration pollInterval;
        private JsonObject result;
        private JsonObject error;
        private JsonObject inputRequests;
        private List<String> outstandingInputRequestKeys;
        private JsonObject inputResponses;
        private String cancellationReason;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder serverName(String serverName) {
            this.serverName = serverName;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder executionNodeId(String executionNodeId) {
            this.executionNodeId = executionNodeId;
            return this;
        }

        public Builder version(long version) {
            this.version = version;
            return this;
        }

        public Builder status(TaskStatus status) {
            this.status = status;
            return this;
        }

        public Builder statusMessage(String statusMessage) {
            this.statusMessage = statusMessage;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder lastUpdatedAt(Instant lastUpdatedAt) {
            this.lastUpdatedAt = lastUpdatedAt;
            return this;
        }

        /**
         * @param ttl the time-to-live, or {@code null} if unlimited
         * @return self
         */
        public Builder ttl(Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        public Builder pollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
            return this;
        }

        /**
         * @param result the {@code CallToolResult}; the value is normalized
         * @return self
         */
        public Builder result(JsonObject result) {
            this.result = normalize(result);
            return this;
        }

        /**
         * @param error the JSON-RPC error; the value is normalized
         * @return self
         */
        public Builder error(JsonObject error) {
            this.error = normalize(error);
            return this;
        }

        /**
         * @param inputRequests all the input requests sent during the lifetime of the task; the value is
         *        normalized
         * @return self
         */
        public Builder inputRequests(JsonObject inputRequests) {
            this.inputRequests = normalize(inputRequests);
            return this;
        }

        /**
         * Adds the given input requests to the current ones. Unlike {@link #inputRequests(JsonObject)}, only the added value
         * is normalized.
         *
         * @param inputRequests the input requests to add
         * @return self
         */
        public Builder addInputRequests(JsonObject inputRequests) {
            this.inputRequests = merge(this.inputRequests, inputRequests);
            return this;
        }

        public Builder outstandingInputRequestKeys(List<String> outstandingInputRequestKeys) {
            this.outstandingInputRequestKeys = outstandingInputRequestKeys;
            return this;
        }

        /**
         * @param inputResponses the input responses collected for the last input request; the value is
         *        normalized
         * @return self
         */
        public Builder inputResponses(JsonObject inputResponses) {
            this.inputResponses = normalize(inputResponses);
            return this;
        }

        /**
         * Adds the given input responses to the current ones. Unlike {@link #inputResponses(JsonObject)}, only the added value
         * is normalized.
         *
         * @param inputResponses the input responses to add
         * @return self
         */
        public Builder addInputResponses(JsonObject inputResponses) {
            this.inputResponses = merge(this.inputResponses, inputResponses);
            return this;
        }

        public Builder cancellationReason(String cancellationReason) {
            this.cancellationReason = cancellationReason;
            return this;
        }

        /**
         * @return a new record
         * @throws NullPointerException if a required value is missing, i.e. {@code id}, {@code serverName},
         *         {@code toolName}, {@code executionNodeId}, {@code status}, {@code createdAt} or {@code pollInterval}
         */
        public TaskRecord build() {
            return new TaskRecord(this);
        }

    }

}
