package io.quarkiverse.mcp.server.runtime;

import java.util.Map;

import io.quarkiverse.mcp.server.McpConnection;

/**
 * Holds all information needed to supply arguments for a feature method.
 */
public record ArgumentProviders(
        Map<String, Object> args,
        McpConnection connection,
        Object requestId,
        String uri,
        Sender sender,
        Object progressToken,
        ResponseHandlers responseHandlers,
        String serverName) {

    Object getArg(String name) {
        return args != null ? args.get(name) : null;
    }

}
