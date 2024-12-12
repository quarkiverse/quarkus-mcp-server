package io.quarkiverse.mcp.server.runtime;

import java.util.Map;

import io.quarkiverse.mcp.server.McpConnection;

public record ArgumentProviders(Map<String, Object> args, McpConnection connection, Object requestId) {

}
