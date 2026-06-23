package io.quarkiverse.mcp.server.runtime;

import java.util.List;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpTrafficListener;
import io.quarkiverse.mcp.server.RawMessage;
import io.quarkus.arc.All;
import io.vertx.core.json.JsonObject;

@Singleton
public class TrafficListeners {

    private static final Logger LOG = Logger.getLogger(TrafficListeners.class);

    private final List<McpTrafficListener> listeners;

    TrafficListeners(@All List<McpTrafficListener> listeners) {
        this.listeners = listeners;
    }

    public void messageReceived(JsonObject message, McpConnectionBase connection) {
        if (listeners.isEmpty()) {
            return;
        }
        RawMessage rawMessage = new RawMessageImpl(message);
        for (McpTrafficListener listener : listeners) {
            try {
                listener.onMessageReceived(rawMessage, connection);
            } catch (RuntimeException e) {
                LOG.errorf(e, "Error in traffic listener [%s]", listener.getClass().getName());
            }
        }
    }

    public void messageSent(JsonObject message, McpConnectionBase connection) {
        if (listeners.isEmpty()) {
            return;
        }
        RawMessage rawMessage = new RawMessageImpl(message);
        for (McpTrafficListener listener : listeners) {
            try {
                listener.onMessageSent(rawMessage, connection);
            } catch (RuntimeException e) {
                LOG.errorf(e, "Error in traffic listener [%s]", listener.getClass().getName());
            }
        }
    }

}
