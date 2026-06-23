package io.quarkiverse.mcp.server.runtime;

import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.vertx.core.json.JsonObject;

public interface McpRequest {

    String serverName();

    JsonObject message();

    McpConnectionBase connection();

    Sender sender();

    SecuritySupport securitySupport();

    ContextSupport contextSupport();

    default void markReceived() {
        connection().trafficListeners().messageReceived(message(), connection());
    }

    default void messageSent(JsonObject message) {
        connection().trafficListeners().messageSent(message, connection());
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

    McpProtocolVersion protocolVersion();
}