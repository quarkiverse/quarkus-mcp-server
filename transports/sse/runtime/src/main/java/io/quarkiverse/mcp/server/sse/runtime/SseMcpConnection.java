package io.quarkiverse.mcp.server.sse.runtime;

import java.time.Duration;
import java.util.Optional;

import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
import io.quarkiverse.mcp.server.runtime.TrafficLogger;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;

public class SseMcpConnection extends McpConnectionBase {

    private final HttpServerResponse response;

    SseMcpConnection(String id, LogLevel defaultLogLevel, TrafficLogger trafficLogger, Optional<Duration> autoPingInterval,
            HttpServerResponse response) {
        super(id, defaultLogLevel, trafficLogger, autoPingInterval);
        this.response = response;
    }

    public void sendEvent(String name, String data) {
        // "write" is async and synchronized over http connection, and should be thread-safe
        response.write("event: " + name + "\ndata: " + data + "\n\n");
    }

    @Override
    public void send(JsonObject message) {
        if (message == null) {
            return;
        }
        if (trafficLogger != null) {
            trafficLogger.messageSent(message, this);
        }
        sendEvent("message", message.encode());
    }

}
