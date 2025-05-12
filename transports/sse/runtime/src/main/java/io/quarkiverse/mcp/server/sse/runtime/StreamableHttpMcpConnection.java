package io.quarkiverse.mcp.server.sse.runtime;

import java.time.Duration;
import java.util.Optional;

import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
import io.quarkiverse.mcp.server.runtime.TrafficLogger;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

class StreamableHttpMcpConnection extends McpConnectionBase {

    StreamableHttpMcpConnection(String id, LogLevel defaultLogLevel, TrafficLogger trafficLogger,
            Optional<Duration> autoPingInterval) {
        super(id, defaultLogLevel, trafficLogger, autoPingInterval);
    }

    @Override
    public Future<Void> send(JsonObject message) {
        throw new UnsupportedOperationException();
    }

}
