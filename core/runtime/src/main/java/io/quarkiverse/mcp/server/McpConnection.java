package io.quarkiverse.mcp.server;

import io.quarkiverse.mcp.server.McpLog.LogLevel;

/**
 * The connection from an MCP client.
 */
public interface McpConnection {

    /**
     * @return the identifier (not {@code null})}
     */
    String id();

    /**
     * The connection status is not relevant for {@linkplain #isTransient()
     * transient} connections, which are always {@link Status#IN_OPERATION}.
     *
     * @return the current status (not {@code null})}
     */
    Status status();

    /**
     * For {@linkplain #isTransient() transient} connections, the initial request is synthesized from per-request {@code _meta}
     * data (protocol version, client info, client capabilities).
     *
     * @return the initial request (not {@code null})}
     */
    InitialRequest initialRequest();

    /**
     * For {@linkplain #isTransient() transient} connections, returns the per-request log level from
     * {@code _meta} ({@code io.modelcontextprotocol/logLevel}), or the default level if not specified.
     *
     * @return the current log level
     */
    LogLevel logLevel();

    /**
     * @return the name of server configuration
     * @see McpServer
     */
    String serverName();

    /**
     * A transient connection is created per-request and is not tracked on the server. Stateless clients and
     * auto-initialized connections use transient connections.
     *
     * @return {@code true} if this connection is transient
     */
    boolean isTransient();

    enum Status {

        /**
         * A new connection, waiting for the {@code initialize} request from the client.
         */
        NEW,
        /**
         * The server responded to the {@code initialize} request with its own capabilities and information. Now it's waiting
         * for the {@code initialized} notification from the client.
         */
        INITIALIZING,
        /**
         * The client sent the {@code initialized} notification.
         */
        IN_OPERATION,
        /**
         * Connection was closed.
         */
        CLOSED;

        public boolean isClientInitialized() {
            return this == IN_OPERATION;
        }

    }

}
