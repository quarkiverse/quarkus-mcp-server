package io.quarkiverse.mcp.server.sse.runtime;

import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

class StreamableHttpMcpConnection extends McpConnectionBase {

    StreamableHttpMcpConnection(String id, McpServerRuntimeConfig serverConfig) {
        super(id, serverConfig);
    }

    @Override
    public Future<Void> send(JsonObject message) {
        throw new UnsupportedOperationException();
    }

}
