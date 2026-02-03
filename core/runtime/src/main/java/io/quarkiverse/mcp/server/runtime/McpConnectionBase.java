package io.quarkiverse.mcp.server.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.RequestId;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.vertx.core.json.JsonObject;

public abstract class McpConnectionBase implements McpConnection, Sender {

    protected final String id;

    protected final AtomicReference<Status> status;

    protected volatile InitialRequest initializeRequest;

    protected volatile LogLevel logLevel;

    protected final int trafficLoggerTextLimit;

    protected final Optional<Duration> autoPingInterval;

    protected volatile long lastUsed;

    protected final long idleTimeout;

    protected final ConcurrentMap<RequestId, Optional<String>> cancellationRequests;

    protected McpConnectionBase(String id, McpServerRuntimeConfig serverConfig) {
        this.id = id;
        this.status = new AtomicReference<>(Status.NEW);
        this.logLevel = serverConfig.clientLogging().defaultLevel();
        this.trafficLoggerTextLimit = serverConfig.trafficLogging().enabled()
                ? serverConfig.trafficLogging().textLimit()
                : -1;
        this.autoPingInterval = serverConfig.autoPingInterval();
        this.lastUsed = Instant.now().toEpochMilli();
        this.idleTimeout = serverConfig.connectionIdleTimeout().toMillis();
        this.cancellationRequests = new ConcurrentHashMap<>();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Status status() {
        return status.get();
    }

    @Override
    public InitialRequest initialRequest() {
        return initializeRequest;
    }

    public boolean initialize(InitialRequest request) {
        if (status.compareAndSet(Status.NEW, Status.INITIALIZING)) {
            this.initializeRequest = request;
            return true;
        }
        return false;
    }

    public boolean close() {
        return status.compareAndSet(Status.IN_OPERATION, Status.CLOSED);
    }

    @Override
    public LogLevel logLevel() {
        return logLevel;
    }

    void setLogLevel(LogLevel level) {
        this.logLevel = level;
    }

    public boolean setInitialized() {
        return status.compareAndSet(Status.INITIALIZING, Status.IN_OPERATION);
    }

    public int getTrafficLoggerTextLimit() {
        return trafficLoggerTextLimit;
    }

    public Optional<Duration> autoPingInterval() {
        return autoPingInterval;
    }

    public McpConnectionBase touch() {
        this.lastUsed = Instant.now().toEpochMilli();
        return this;
    }

    public boolean isIdleTimeoutExpired() {
        if (idleTimeout <= 0) {
            return false;
        }
        return Instant.now().minusMillis(lastUsed).toEpochMilli() > idleTimeout;
    }

    protected void messageSent(JsonObject message) {
        if (trafficLoggerTextLimit > 0) {
            TrafficLogger.messageSent(message, this, trafficLoggerTextLimit);
        }
    }

    Optional<String> getCancellationRequest(RequestId requestId) {
        return cancellationRequests.get(requestId);
    }

    boolean addCancellationRequest(RequestId requestId, String reason) {
        return cancellationRequests.putIfAbsent(requestId, Optional.ofNullable(reason)) == null;
    }

    void removeCancellationRequest(JsonObject request) {
        if (!cancellationRequests.isEmpty()) {
            cancellationRequests.remove(new RequestId(Messages.getId(request)));
        }
    }

}
