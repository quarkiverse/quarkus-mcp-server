package io.quarkiverse.mcp.server.runtime;

import org.jboss.logging.Logger;

import io.vertx.core.json.JsonObject;

public class TrafficLogger {

    private static final Logger LOG = Logger.getLogger("io.quarkus.mcp.server.traffic");

    private final int textPayloadLimit;

    public TrafficLogger(int textPayloadLimit) {
        this.textPayloadLimit = textPayloadLimit;
    }

    public void messageReceived(JsonObject message) {
        LOG.infof("JSON message received:\n\n%s", messageToString(message));
    }

    public void messageSent(JsonObject message) {
        LOG.infof("JSON message sent:\n\n%s", messageToString(message));
    }

    private String messageToString(JsonObject message) {
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
