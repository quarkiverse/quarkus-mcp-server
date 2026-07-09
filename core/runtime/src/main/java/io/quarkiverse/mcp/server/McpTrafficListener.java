package io.quarkiverse.mcp.server;

/**
 * Any CDI bean that implements this interface is notified when an MCP message is sent or received.
 * <p>
 * Listeners should be fast and efficient, and should never block the current thread (read data from a socket, write data to
 * disk, etc.). If a listener throws an unchecked exception then it is logged and the next listener is applied.
 */
public interface McpTrafficListener {

    /**
     * Called when an MCP message is received from a client.
     *
     * @param message the raw message (must not be {@code null})
     * @param connection the MCP connection (must not be {@code null})
     */
    default void onMessageReceived(RawMessage message, McpConnection connection) {
    }

    /**
     * Called when an MCP message is sent to a client.
     *
     * @param message the raw message (must not be {@code null})
     * @param connection the MCP connection (must not be {@code null})
     */
    default void onMessageSent(RawMessage message, McpConnection connection) {
    }

    /**
     * Returns {@code true} if this listener is enabled and should be notified about traffic messages.
     * <p>
     * A disabled listener is completely skipped during traffic processing.
     *
     * @return {@code true} if this listener is enabled
     */
    default boolean isEnabled() {
        return true;
    }

}
