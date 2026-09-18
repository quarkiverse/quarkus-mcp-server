package io.quarkiverse.mcp.server.runtime;

import java.util.Set;

/**
 * Contributes a custom filter to the {@code subscriptions/listen} request, i.e. an array of keys declared in the
 * {@code notifications} object, and routes the corresponding notifications to the matching subscriptions.
 * <p>
 * This is an internal extension point used by MCP extensions (e.g. the {@code taskIds} filter of MCP Tasks). Implementations
 * are CDI beans. A notification is delivered to a subscription via
 * {@link McpConnectionBase#sendNotification(io.vertx.core.json.JsonObject, String)} where the key must be one of the
 * accepted keys.
 */
public interface SubscriptionFilterExtension {

    /**
     * @return the name of the array field in the {@code notifications} object, e.g. {@code taskIds}
     */
    String filterName();

    /**
     * @return the JSON-RPC method of the notifications routed by this filter, e.g. {@code notifications/tasks}
     */
    String notificationMethod();

    /**
     * Validates the keys declared by the client and returns the keys the server agrees to notify about; the returned set is
     * included in the {@code notifications/subscriptions/acknowledged} notification.
     *
     * @param keys the keys declared by the client, never empty
     * @param mcpRequest the {@code subscriptions/listen} request
     * @return the accepted keys, may be empty
     * @throws io.quarkiverse.mcp.server.McpException if the request must be rejected
     */
    Set<String> accept(Set<String> keys, McpRequest mcpRequest);

}
