package io.quarkiverse.mcp.server.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;

public abstract class McpConnectionBase implements McpConnection, Sender {

    protected final String id;

    protected final AtomicReference<Status> status;

    protected final AtomicReference<InitialRequest> initializeRequest;

    protected final AtomicReference<LogLevel> logLevel;

    protected final TrafficLogger trafficLogger;

    protected final Optional<Duration> autoPingInterval;

    protected final AtomicLong lastUsed;

    protected final long idleTimeout;

    protected McpConnectionBase(String id, McpServerRuntimeConfig serverConfig) {
        this.id = id;
        this.status = new AtomicReference<>(Status.NEW);
        this.initializeRequest = new AtomicReference<>();
        this.logLevel = new AtomicReference<>(serverConfig.clientLogging().defaultLevel());
        this.trafficLogger = serverConfig.trafficLogging().enabled()
                ? new TrafficLogger(serverConfig.trafficLogging().textLimit())
                : null;
        this.autoPingInterval = serverConfig.autoPingInterval();
        this.lastUsed = new AtomicLong(Instant.now().toEpochMilli());
        this.idleTimeout = serverConfig.connectionIdleTimeout().toMillis();
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
        return initializeRequest.get();
    }

    public boolean initialize(InitialRequest request) {
        if (status.compareAndSet(Status.NEW, Status.INITIALIZING)) {
            initializeRequest.set(request);
            return true;
        }
        return false;
    }

    public boolean close() {
        return status.compareAndSet(Status.IN_OPERATION, Status.CLOSED);
    }

    @Override
    public LogLevel logLevel() {
        return logLevel.get();
    }

    void setLogLevel(LogLevel level) {
        this.logLevel.set(level);
    }

    public boolean setInitialized() {
        return status.compareAndSet(Status.INITIALIZING, Status.IN_OPERATION);
    }

    public TrafficLogger trafficLogger() {
        return trafficLogger;
    }

    public Optional<Duration> autoPingInterval() {
        return autoPingInterval;
    }

    public McpConnectionBase touch() {
        this.lastUsed.set(Instant.now().toEpochMilli());
        return this;
    }

    public boolean isIdleTimeoutExpired() {
        if (idleTimeout <= 0) {
            return false;
        }
        return Instant.now().minusMillis(lastUsed.get()).toEpochMilli() > idleTimeout;
    }

}
