package io.quarkiverse.mcp.server.runtime;

import java.util.concurrent.atomic.AtomicReference;

import io.quarkiverse.mcp.server.InitializeRequest;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpLog.LogLevel;

public abstract class McpConnectionBase implements McpConnection {

    private final String id;

    private final AtomicReference<Status> status;

    private final AtomicReference<InitializeRequest> initializeRequest;

    private final AtomicReference<LogLevel> logLevel;

    protected McpConnectionBase(String id, LogLevel defaultLogLevel) {
        this.id = id;
        this.status = new AtomicReference<>(Status.NEW);
        this.initializeRequest = new AtomicReference<>();
        this.logLevel = new AtomicReference<>(defaultLogLevel);
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
    public InitializeRequest initializeRequest() {
        return initializeRequest.get();
    }

    public boolean initialize(InitializeRequest request) {
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

}
