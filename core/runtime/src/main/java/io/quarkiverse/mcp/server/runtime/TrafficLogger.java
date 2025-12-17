package io.quarkiverse.mcp.server.runtime;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpConnection;
import io.vertx.core.json.JsonObject;

public final class TrafficLogger {

    private static final Logger LOG = Logger.getLogger("io.quarkus.mcp.server.traffic");

    public static void messageReceived(JsonObject message, McpConnection connection, int textPayloadLimit) {
        LOG.infof("MCP message received [%s]:\n\n%s", connection.id(), messageToString(message, textPayloadLimit));
    }

    public static void messageSent(JsonObject message, McpConnection connection, int textPayloadLimit) {
        LOG.infof("MCP message sent [%s]:\n\n%s", connection.id(), messageToString(message, textPayloadLimit));
    }

    private static String messageToString(JsonObject message, int textPayloadLimit) {
        String encoded = message.encodePrettily();
        if (encoded == null || encoded.isBlank()) {
            return "n/a";
        } else if (textPayloadLimit < 0 || encoded.length() <= textPayloadLimit) {
            return encoded;
        } else {
            return encoded.substring(0, textPayloadLimit) + "...";
        }
    }

}
