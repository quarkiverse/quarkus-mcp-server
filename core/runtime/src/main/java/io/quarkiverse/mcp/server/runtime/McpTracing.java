package io.quarkiverse.mcp.server.runtime;

import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.McpMethod;
import io.vertx.core.json.JsonObject;

public interface McpTracing {

    /**
     * Starts a tracing span. Returns a handle to end the span later.
     * Implementations must never return null.
     */
    McpTracingSpan startSpan(McpMethod method, JsonObject message, McpRequest mcpRequest, InitialRequest.Transport transport);

    /**
     * Injects the current trace context into the given {@code _meta} JSON object.
     * Used for server-to-client callbacks (sampling, elicitation, roots) to propagate
     * the MCP server span context to the client.
     * <p>
     * The default implementation is a no-op for when tracing is disabled.
     *
     * @param meta the {@code _meta} JSON object to inject trace context into
     */
    default void injectMcpOtelContext(JsonObject meta) {
        // no-op when tracing is disabled
    }
}
