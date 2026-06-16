package io.quarkiverse.mcp.server.runtime;

/**
 * Represents an active subscription opened via {@code subscriptions/listen}.
 *
 * @param subscriptionId the JSON-RPC request ID of the {@code subscriptions/listen} call
 * @param filter the notification filter specifying which notification types the client subscribed to
 */
public record Subscription(Object subscriptionId, SubscriptionFilter filter) {

    public Subscription {
        if (subscriptionId == null) {
            throw new IllegalArgumentException("subscriptionId must not be null");
        }
        if (filter == null) {
            throw new IllegalArgumentException("filter must not be null");
        }
    }
}
