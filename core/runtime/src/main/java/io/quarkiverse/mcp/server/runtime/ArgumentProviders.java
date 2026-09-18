package io.quarkiverse.mcp.server.runtime;

import java.util.Map;

import io.vertx.core.json.JsonObject;

/**
 * Holds all information needed to supply arguments for a feature method.
 *
 * @param customProviders objects supplied by an MCP extension (e.g. via {@link ToolCallInterceptor}) keyed by type; they
 *        back the {@link FeatureArgument.Provider#CUSTOM} arguments and may override the built-in providers, may be
 *        {@code null}
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
        Map<Class<?>, Object> customProviders) {

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

    /**
     * @param <T> the type
     * @param type the type
     * @return the custom provider object of the given type, or {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T customProvider(Class<T> type) {
        return customProviders != null ? (T) customProviders.get(type) : null;
    }

    Object getArg(String name) {
        return args != null ? args.get(name) : null;
    }

}
