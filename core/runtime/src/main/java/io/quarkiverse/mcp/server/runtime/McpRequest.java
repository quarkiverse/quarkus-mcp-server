package io.quarkiverse.mcp.server.runtime;

import io.vertx.core.json.JsonObject;

public interface McpRequest {

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

    void operationStart();

    void operationEnd();
}