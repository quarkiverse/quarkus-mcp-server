package io.quarkiverse.mcp.server.runtime;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.inject.Singleton;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.RequestId;
import io.vertx.core.json.JsonObject;

@Singleton
public class CancellationRequests {

    protected final ConcurrentMap<CancellationRequestKey, Optional<String>> cancellationRequests;

    public CancellationRequests() {
        this.cancellationRequests = new ConcurrentHashMap<>();
    }

    Optional<String> get(McpConnection connection, RequestId requestId) {
        return cancellationRequests.get(new CancellationRequestKey(connection.id(), requestId));
    }

    boolean add(McpConnection connection, RequestId requestId, String reason) {
        return cancellationRequests.putIfAbsent(new CancellationRequestKey(connection.id(), requestId),
                Optional.ofNullable(reason)) == null;
    }

    void remove(McpConnection connection, JsonObject request) {
        if (!cancellationRequests.isEmpty()) {
            cancellationRequests.remove(new CancellationRequestKey(connection.id(), new RequestId(Messages.getId(request))));
        }
    }

    private record CancellationRequestKey(String connectionId, RequestId requestId) {
    }
}
