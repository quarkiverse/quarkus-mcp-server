package io.quarkiverse.mcp.server.runtime;

import io.quarkiverse.mcp.server.McpMethod;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface McpRequestValidator {

    /**
     *
     * @param message
     * @param mcpRequest
     * @param method
     * @return {@code true} if the message is valid and should be processed, {@code false} otherwise
     */
    <MCP_REQUEST extends McpRequest> Future<Boolean> validate(JsonObject message, MCP_REQUEST mcpRequest, McpMethod method);

}
