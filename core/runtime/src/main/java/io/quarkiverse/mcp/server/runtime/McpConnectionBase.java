package io.quarkiverse.mcp.server.runtime;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpLog.LogLevel;

public abstract class McpConnectionBase implements McpConnection, Sender {

    protected final String id;

    protected final AtomicReference<Status> status;

    protected final AtomicReference<InitialRequest> initializeRequest;

    protected final AtomicReference<LogLevel> logLevel;

    protected final TrafficLogger trafficLogger;

    protected final Optional<Duration> autoPingInterval;

    protected McpConnectionBase(String id, LogLevel defaultLogLevel, TrafficLogger trafficLogger,
            Optional<Duration> autoPingInterval) {
        this.id = id;
        this.status = new AtomicReference<>(Status.NEW);
        this.initializeRequest = new AtomicReference<>();
        this.logLevel = new AtomicReference<>(defaultLogLevel);
        this.trafficLogger = trafficLogger;
        this.autoPingInterval = autoPingInterval;
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

}
