package io.quarkiverse.mcp.server;

/**
 * Service for tracking cancelled MCP requests
 */
public interface Cancellations {

    /**
     * Check if a request has been cancelled (MCP compliant)
     *
     * @param requestId the request identifier from MCP protocol
     * @return true if the request has been cancelled, false otherwise
     */
    boolean isCancelled(RequestId requestId);

    /**
     * Check if a request has been cancelled for a specific connection
     *
     * @param connectionId the connection identifier
     * @param requestId the request identifier
     * @return true if the request has been cancelled, false otherwise
     */
    boolean isCancelled(String connectionId, RequestId requestId);
}
