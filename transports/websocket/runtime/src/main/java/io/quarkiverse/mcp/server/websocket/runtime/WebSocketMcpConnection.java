package io.quarkiverse.mcp.server.websocket.runtime;

import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
import io.quarkiverse.mcp.server.runtime.TrafficListeners;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.vertx.UniHelper;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class WebSocketMcpConnection extends McpConnectionBase {

    private final WebSocketConnection connection;

    WebSocketMcpConnection(String id, McpServerRuntimeConfig serverConfig, String serverName,
            TrafficListeners trafficListeners,
            WebSocketConnection connection) {
        this(id, serverConfig, serverName, trafficListeners, connection, false);
    }

    WebSocketMcpConnection(String id, McpServerRuntimeConfig serverConfig, String serverName,
            TrafficListeners trafficListeners,
            WebSocketConnection connection, boolean transientConnection) {
        super(id, serverConfig, serverName, trafficListeners, transientConnection);
        this.connection = connection;
    }

    WebSocketConnection webSocketConnection() {
        return connection;
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
