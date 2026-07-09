package io.quarkiverse.mcp.server.runtime;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpTrafficListener;
import io.quarkiverse.mcp.server.RawMessage;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;

@Singleton
public class TrafficLogger implements McpTrafficListener {

    private static final Logger LOG = Logger.getLogger("io.quarkus.mcp.server.traffic");

    private final boolean anyServerEnabled;
    private final McpServersRuntimeConfig serversConfig;

    TrafficLogger(McpServersRuntimeConfig serversConfig) {
        this.serversConfig = serversConfig;
        boolean enabled = false;
        for (McpServerRuntimeConfig serverConfig : serversConfig.servers().values()) {
            if (serverConfig.trafficLogging().enabled()) {
                enabled = true;
                break;
            }
        }
        this.anyServerEnabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return anyServerEnabled;
    }

    @Override
    public void onMessageReceived(RawMessage message, McpConnection connection) {
        McpServerRuntimeConfig.TrafficLogging config = trafficLoggingConfig(connection);
        if (!config.enabled()) {
            return;
        }
        LOG.infof("MCP message received [%s]:\n\n%s", connection.id(),
                messageToString(message, config.textLimit()));
    }

    @Override
    public void onMessageSent(RawMessage message, McpConnection connection) {
        McpServerRuntimeConfig.TrafficLogging config = trafficLoggingConfig(connection);
        if (!config.enabled()) {
            return;
        }
        LOG.infof("MCP message sent [%s]:\n\n%s", connection.id(),
                messageToString(message, config.textLimit()));
    }

    private McpServerRuntimeConfig.TrafficLogging trafficLoggingConfig(McpConnection connection) {
        return serversConfig.servers().get(connection.serverName()).trafficLogging();
    }

    private static String messageToString(RawMessage message, int textLimit) {
        String encoded = message.asPrettyString();
        if (encoded == null || encoded.isBlank()) {
            return "n/a";
        } else if (textLimit < 0 || encoded.length() <= textLimit) {
            return encoded;
        } else {
            return encoded.substring(0, textLimit) + "...";
        }
    }

}
