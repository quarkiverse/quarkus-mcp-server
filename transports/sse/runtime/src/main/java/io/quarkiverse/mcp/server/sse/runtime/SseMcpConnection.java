package io.quarkiverse.mcp.server.sse.runtime;

import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
import io.vertx.core.http.HttpServerResponse;

public class SseMcpConnection extends McpConnectionBase {

    private final HttpServerResponse response;

    SseMcpConnection(String id, LogLevel defaultLogLevel, HttpServerResponse response) {
        super(id, defaultLogLevel);
        this.response = response;
    }

    public void sendEvent(String name, String data) {
        response.write("event: " + name + "\n");
        response.write("data: " + data + "\n");
        response.write("\n");
    }

}
