package io.quarkiverse.mcp.server.runtime;

import java.util.Map;

import io.vertx.core.json.JsonObject;

/**
 * Holds all information needed to supply arguments for a feature method.
 */
record ArgumentProviders(
        JsonObject rawMessage,
        Map<String, Object> args,
        McpConnectionBase connection,
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
