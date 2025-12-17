package io.quarkiverse.mcp.server.websocket.runtime;

import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.vertx.UniHelper;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class WebSocketMcpConnection extends McpConnectionBase {

    private final WebSocketConnection connection;

    WebSocketMcpConnection(String id, McpServerRuntimeConfig serverConfig, WebSocketConnection connection) {
        super(id, serverConfig);
        this.connection = connection;
    }

    @Override
    public Future<Void> send(JsonObject message) {
        if (message == null) {
            return Future.succeededFuture();
        }
        messageSent(message);
        return UniHelper.toFuture(connection.sendText(message.encode()));
    }

}
