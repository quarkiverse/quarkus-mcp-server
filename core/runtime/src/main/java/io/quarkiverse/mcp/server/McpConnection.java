package io.quarkiverse.mcp.server;

import io.quarkiverse.mcp.server.McpLog.LogLevel;

/**
 * Connection from an MCP client.
 */
public interface McpConnection {

    String id();

    Status status();

    boolean initialize(InitializeRequest request);

    boolean setInitialized();

    InitializeRequest initializeRequest();

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
