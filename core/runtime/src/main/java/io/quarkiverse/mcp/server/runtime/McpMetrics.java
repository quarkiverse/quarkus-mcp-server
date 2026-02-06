package io.quarkiverse.mcp.server.runtime;

import java.util.function.ToDoubleFunction;

import io.quarkiverse.mcp.server.McpMethod;
import io.vertx.core.json.JsonObject;

public interface McpMetrics {

    <T> void createMcpConnectionsGauge(T stateObject, ToDoubleFunction<T> valueFunction);

    void mcpRequestCompleted(McpMethod method, JsonObject message, McpRequest mcpRequest, long duration, Throwable failure);
}
