package io.quarkiverse.mcp.server.runtime;

import java.util.Map;

import io.quarkiverse.mcp.server.McpConnection;

public record ArgumentProviders(Map<String, Object> args, McpConnection connection, Object requestId, String uri,
        Sender sender, Object progressToken, ResponseHandlers responseHandlers) {

    Object getArg(String name) {
        return args != null ? args.get(name) : null;
    }

}
