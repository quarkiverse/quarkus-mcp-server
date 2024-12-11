package io.quarkiverse.mcp.server.runtime;

import java.util.concurrent.atomic.AtomicReference;

import io.quarkiverse.mcp.server.InitializeRequest;
import io.quarkiverse.mcp.server.McpConnection;
import io.vertx.core.http.HttpServerResponse;

class McpConnectionImpl implements McpConnection {

    private final String id;

    private final AtomicReference<Status> status;

    private final HttpServerResponse response;

    private final AtomicReference<InitializeRequest> initializeRequest;

    McpConnectionImpl(String id, HttpServerResponse response) {
        this.id = id;
        this.status = new AtomicReference<>(Status.NEW);
        this.response = response;
        this.initializeRequest = new AtomicReference<>();
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

    boolean initialize(InitializeRequest request) {
        if (status.compareAndSet(Status.NEW, Status.INITIALIZING)) {
            initializeRequest.set(request);
            return true;
        }
        return false;
    }

    boolean initialized() {
        return status.compareAndSet(Status.INITIALIZING, Status.IN_OPERATION);
    }

    void sendEvent(String name, String data) {
        response.write("event: " + name + "\n");
        response.write("data: " + data + "\n");
        response.write("\n\n");
    }

}
