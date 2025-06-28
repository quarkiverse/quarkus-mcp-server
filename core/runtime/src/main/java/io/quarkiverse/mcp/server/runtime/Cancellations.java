package io.quarkiverse.mcp.server.runtime;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.inject.Singleton;

@Singleton
public class Cancellations {

    // Map of connection ID -> set of cancelled request IDs
    private final ConcurrentMap<String, Set<Object>> cancellationsByConnection = new ConcurrentHashMap<>();

    /**
     * Check if a request has been cancelled (MCP compliant)
     *
     * @param requestId the request identifier from MCP protocol
     * @return true if the request has been cancelled, false otherwise
     */
    public boolean isCancelled(Object requestId) {
        return cancellationsByConnection.values().stream()
                .anyMatch(cancelledRequests -> cancelledRequests.contains(requestId));
    }

    /**
     * Check if a request has been cancelled for a specific connection
     */
    public boolean isCancelled(String connectionId, Object requestId) {
        Set<Object> cancelledRequests = cancellationsByConnection.get(connectionId);
        return cancelledRequests != null && cancelledRequests.contains(requestId);
    }

    /**
     * Mark a request as cancelled (called by MCP notification handler)
     * This should be triggered by "notifications/cancelled" messages
     */
    public void cancelRequest(String connectionId, Object requestId) {
        cancellationsByConnection.computeIfAbsent(connectionId, k -> ConcurrentHashMap.newKeySet())
                .add(requestId);
    }

    /**
     * Remove a request from tracking (when request completes)
     */
    public void removeRequest(String connectionId, Object requestId) {
        Set<Object> cancelledRequests = cancellationsByConnection.get(connectionId);
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
    public void cleanupConnection(String connectionId) {
        cancellationsByConnection.remove(connectionId);
    }
}
