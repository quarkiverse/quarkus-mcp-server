package io.quarkiverse.mcp.server.runtime;

import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.McpMethod;
import io.vertx.core.json.JsonObject;

public interface McpRequest {

    String serverName();

    Object json();

    McpConnectionBase connection();

    Sender sender();

    SecuritySupport securitySupport();

    ContextSupport contextSupport();

    default void messageReceived(JsonObject message) {
        if (connection().getTrafficLoggerTextLimit() > 0) {
            TrafficLogger.messageReceived(message, connection(), connection().getTrafficLoggerTextLimit());
        }
    }

    default void messageSent(JsonObject message) {
        if (connection().getTrafficLoggerTextLimit() > 0) {
            TrafficLogger.messageSent(message, connection(), connection().getTrafficLoggerTextLimit());
        }
    }

    /**
     * Prepare and start the tracing span for this request.
     */
    void prepareTracing(McpTracing mcpTracing, McpMethod method, JsonObject message, InitialRequest.Transport transport);

    /**
     * Set error response info on the tracing span for validation failures.
     */
    void setTracingErrorResponse(boolean toolError, Integer jsonRpcErrorCode, String errorMessage);

    /**
     * End the tracing span. Safe to call multiple times — subsequent calls are no-ops.
     */
    void endTracing(Throwable error);

    void contextStart();

    void contextEnd(Throwable error);

    String protocolVersion();
}