package io.quarkiverse.mcp.server;

/**
 * A CDI event fired during the lifecycle of an {@link McpConnection}.
 *
 * @param connection the connection
 * @param type the lifecycle event type
 */
public record McpConnectionEvent(McpConnection connection, Type type) {

    /**
     * The type of the lifecycle event.
     */
    public enum Type {
        /**
         * The connection transitioned from {@link McpConnection.Status#NEW} to {@link McpConnection.Status#INITIALIZING}.
         * <p>
         * The {@link McpConnection#initialRequest()} is available at this point.
         */
        INITIALIZING,
        /**
         * The connection transitioned from {@link McpConnection.Status#INITIALIZING} to
         * {@link McpConnection.Status#IN_OPERATION}.
         */
        INITIALIZED,
        /**
         * The connection transitioned to {@link McpConnection.Status#CLOSED}.
         */
        CLOSED
    }
}
