package io.quarkiverse.mcp.server.runtime;

import java.util.Map;

import io.vertx.core.json.JsonObject;

/**
 * Holds all information needed to supply arguments for a feature method.
 *
 * @param task the task if the feature method is a tool executed as a task (MCP Tasks extension), {@code null} otherwise
 */
public record ArgumentProviders(
        JsonObject rawMessage,
        Map<String, Object> args,
        McpConnectionBase connection,
        Object requestId,
        String uri,
        Sender sender,
        Object progressToken,
        ServerRequests serverRequests,
        String serverName,
        CancellationRequests cancellationRequests,
        McpTracing mcpTracing,
        TaskImpl task) {

    public ArgumentProviders(
            JsonObject rawMessage,
            Map<String, Object> args,
            McpConnectionBase connection,
            Object requestId,
            String uri,
            Sender sender,
            Object progressToken,
            ServerRequests serverRequests,
            String serverName,
            CancellationRequests cancellationRequests,
            McpTracing mcpTracing) {
        this(rawMessage, args, connection, requestId, uri, sender, progressToken, serverRequests, serverName,
                cancellationRequests, mcpTracing, null);
    }

    Object getArg(String name) {
        return args != null ? args.get(name) : null;
    }

}
