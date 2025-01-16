package io.quarkiverse.mcp.server.stdio.runtime;

import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.runtime.McpConnectionBase;

public class StdioMcpConnection extends McpConnectionBase {

    StdioMcpConnection(String id, LogLevel defaultLogLevel) {
        super(id, defaultLogLevel);
    }

}
