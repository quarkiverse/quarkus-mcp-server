package io.quarkiverse.mcp.server.sse.runtime;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

class StreamableHttpMcpConnection extends McpConnectionBase {

    private static final Logger LOG = Logger.getLogger(StreamableHttpMcpConnection.class);

    StreamableHttpMcpConnection(String id, McpServerRuntimeConfig serverConfig) {
        super(id, serverConfig);
    }

    @Override
    public Future<Void> send(JsonObject message) {
        String method = message.getString("method");
        LOG.warnf("Discarding message [%s]- 'subsidiary' SSE streams are not supported yet", method);
        return Future.succeededFuture();
    }

}
