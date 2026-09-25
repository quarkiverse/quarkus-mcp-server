package io.quarkiverse.mcp.server.runtime;

import java.util.List;
import java.util.Set;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Captures the subset of notification types a client subscribed to via {@code subscriptions/listen}.
 */
public record SubscriptionFilter(boolean toolsListChanged, boolean promptsListChanged, boolean resourcesListChanged,
        Set<String> resourceSubscriptions) {

    public static SubscriptionFilter parse(JsonObject notifications) {
        boolean tools = notifications.getBoolean("toolsListChanged", false);
        boolean prompts = notifications.getBoolean("promptsListChanged", false);
        boolean resources = notifications.getBoolean("resourcesListChanged", false);
        Set<String> resourceSubs;
        JsonArray arr = notifications.getJsonArray("resourceSubscriptions");
        if (arr != null && !arr.isEmpty()) {
            String[] uris = new String[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                uris[i] = arr.getString(i);
            }
            resourceSubs = Set.of(uris);
        } else {
            resourceSubs = Set.of();
        }
        return new SubscriptionFilter(tools, prompts, resources, resourceSubs);
    }

    /**
     * @param notificationMethod the JSON-RPC method of the notification
     * @param resourceUri the resource URI for {@code notifications/resources/updated}, or {@code null}
     * @return {@code true} if this filter accepts the given notification
     */
    public boolean matches(String notificationMethod, String resourceUri) {
        if (notificationMethod == null) {
            return false;
        }
        return switch (notificationMethod) {
            case "notifications/tools/list_changed" -> toolsListChanged;
            case "notifications/prompts/list_changed" -> promptsListChanged;
            case "notifications/resources/list_changed" -> resourcesListChanged;
            case "notifications/resources/updated" -> resourceUri != null && resourceSubscriptions.contains(resourceUri);
            default -> false;
        };
    }

    /**
     * @return the {@code notifications} object for the {@code notifications/subscriptions/acknowledged} response
     */
    public JsonObject toAcknowledgedJson() {
        JsonObject ret = new JsonObject();
        if (toolsListChanged) {
            ret.put("toolsListChanged", true);
        }
        if (promptsListChanged) {
            ret.put("promptsListChanged", true);
        }
        if (resourcesListChanged) {
            ret.put("resourcesListChanged", true);
        }
        if (!resourceSubscriptions.isEmpty()) {
            ret.put("resourceSubscriptions", new JsonArray(List.copyOf(resourceSubscriptions)));
        }
        return ret;
    }
}
