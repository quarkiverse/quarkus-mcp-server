package io.quarkiverse.mcp.server;

/**
 * Connection from an MCP client.
 */
public interface McpConnection {

    String id();

    Status status();

    boolean initialize(InitializeRequest request);

    boolean setInitialized();

    InitializeRequest initializeRequest();

    /**
     * https://spec.modelcontextprotocol.io/specification/basic/lifecycle/
     */
    enum Status {
        NEW,
        INITIALIZING,
        IN_OPERATION,
        SHUTDOWN
    }

}
