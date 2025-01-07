package io.quarkiverse.mcp.server.sse.runtime;

import io.quarkiverse.mcp.server.runtime.McpConnectionBase;

public class StdioMcpConnection extends McpConnectionBase {

    StdioMcpConnection(String id) {
        super(id);
    }

}
