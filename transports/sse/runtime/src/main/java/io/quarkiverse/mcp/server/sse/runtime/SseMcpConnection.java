package io.quarkiverse.mcp.server.sse.runtime;

import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;

public class SseMcpConnection extends McpConnectionBase {

    private final HttpServerResponse response;

    SseMcpConnection(String id, McpServerRuntimeConfig serverConfig, HttpServerResponse response) {
        super(id, serverConfig);
        this.response = response;
    }

    public Future<Void> sendEvent(String name, String data) {
        // "write" is async and synchronized over http connection, and should be thread-safe
        return response.write("event: " + name + "\ndata: " + data + "\n\n");
    }

    @Override
    public Future<Void> send(JsonObject message) {
        if (message == null) {
            return Future.succeededFuture();
        }
        if (trafficLogger != null) {
            trafficLogger.messageSent(message, this);
        }
        return sendEvent("message", message.encode());
    }

}
