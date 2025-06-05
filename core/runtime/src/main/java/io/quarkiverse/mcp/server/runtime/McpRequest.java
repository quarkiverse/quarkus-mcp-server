package io.quarkiverse.mcp.server.runtime;

import io.vertx.core.json.JsonObject;

public interface McpRequest {

    String serverName();

    Object json();

    McpConnectionBase connection();

    Sender sender();

    SecuritySupport securitySupport();

    ContextSupport contextSupport();

    default void messageReceived(JsonObject message) {
        if (connection().trafficLogger() != null) {
            connection().trafficLogger().messageReceived(message, connection());
        }
    }

    default void messageSent(JsonObject message) {
        if (connection().trafficLogger() != null) {
            connection().trafficLogger().messageSent(message, connection());
        }
    }

    void contextStart();

    void contextEnd();
}