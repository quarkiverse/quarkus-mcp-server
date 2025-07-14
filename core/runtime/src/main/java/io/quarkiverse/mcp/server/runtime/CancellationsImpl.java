package io.quarkiverse.mcp.server.runtime;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.inject.Singleton;

import io.quarkiverse.mcp.server.Cancellations;
import io.quarkiverse.mcp.server.RequestId;

@Singleton
class CancellationsImpl implements Cancellations {

    // Map of connection ID -> set of cancelled request IDs
    private final ConcurrentMap<String, Set<RequestId>> cancellationsByConnection = new ConcurrentHashMap<>();

    @Override
    public boolean isCancelled(RequestId requestId) {
        return cancellationsByConnection.values().stream()
                .anyMatch(cancelledRequests -> cancelledRequests.contains(requestId));
    }

    @Override
    public boolean isCancelled(String connectionId, RequestId requestId) {
        Set<RequestId> cancelledRequests = cancellationsByConnection.get(connectionId);
        return cancelledRequests != null && cancelledRequests.contains(requestId);
    }

    /**
     * Mark a request as cancelled (called by MCP notification handler)
     * This should be triggered by "notifications/cancelled" messages
     */
    void cancelRequest(String connectionId, RequestId requestId) {
        cancellationsByConnection.computeIfAbsent(connectionId, k -> ConcurrentHashMap.newKeySet())
                .add(requestId);
    }

    /**
     * Remove a request from tracking (when request completes)
     */
    void removeRequest(String connectionId, RequestId requestId) {
        Set<RequestId> cancelledRequests = cancellationsByConnection.get(connectionId);
        if (cancelledRequests != null) {
            cancelledRequests.remove(requestId);
            if (cancelledRequests.isEmpty()) {
                cancellationsByConnection.remove(connectionId);
            }
        }
    }

    /**
     * Clean up all cancellations for a connection (when MCP connection closes)
     */
    void cleanupConnection(String connectionId) {
        cancellationsByConnection.remove(connectionId);
    }
}
