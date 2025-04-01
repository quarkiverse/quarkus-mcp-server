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
     * @return the current status (not {@code null})}
     */
    Status status();

    /**
     * @return the initial request (not {@code null})}
     */
    InitialRequest initialRequest();

    /**
     * @return the current log level
     */
    LogLevel logLevel();

    /**
     * See <a href="https://spec.modelcontextprotocol.io/specification/2024-11-05/basic/lifecycle/">Lifecycle</a>
     */
    enum Status {
        NEW,
        INITIALIZING,
        IN_OPERATION,
        SHUTDOWN
    }

}
