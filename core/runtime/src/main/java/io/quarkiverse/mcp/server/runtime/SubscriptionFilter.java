package io.quarkiverse.mcp.server.runtime;

import java.util.List;
import java.util.Set;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Captures the subset of notification types a client subscribed to via {@code subscriptions/listen}.
 */
public record SubscriptionFilter(boolean toolsListChanged, boolean promptsListChanged, boolean resourcesListChanged,
        Set<String> resourceSubscriptions, Set<String> taskIds) {

    public SubscriptionFilter(boolean toolsListChanged, boolean promptsListChanged, boolean resourcesListChanged,
            Set<String> resourceSubscriptions) {
        this(toolsListChanged, promptsListChanged, resourcesListChanged, resourceSubscriptions, Set.of());
    }

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
        return new SubscriptionFilter(tools, prompts, resources, resourceSubs, parseStrings(notifications, "taskIds"));
    }

    private static Set<String> parseStrings(JsonObject notifications, String name) {
        JsonArray arr = notifications.getJsonArray(name);
        if (arr != null && !arr.isEmpty()) {
            String[] values = new String[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                values[i] = arr.getString(i);
            }
            return Set.of(values);
        }
        return Set.of();
    }

    /**
     * @param taskIds the task ids to keep
     * @return a copy of this filter with the given task ids
     */
    public SubscriptionFilter withTaskIds(Set<String> taskIds) {
        return new SubscriptionFilter(toolsListChanged, promptsListChanged, resourcesListChanged, resourceSubscriptions,
                Set.copyOf(taskIds));
    }

    /**
     * @param notificationMethod the JSON-RPC method of the notification
     * @param key the resource URI for {@code notifications/resources/updated}, the task id for {@code notifications/tasks},
     *        or {@code null}
     * @return {@code true} if this filter accepts the given notification
     */
    public boolean matches(String notificationMethod, String key) {
        if (notificationMethod == null) {
            return false;
        }
        return switch (notificationMethod) {
            case "notifications/tools/list_changed" -> toolsListChanged;
            case "notifications/prompts/list_changed" -> promptsListChanged;
            case "notifications/resources/list_changed" -> resourcesListChanged;
            case "notifications/resources/updated" -> key != null && resourceSubscriptions.contains(key);
            case "notifications/tasks" -> key != null && taskIds.contains(key);
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
        if (!taskIds.isEmpty()) {
            ret.put("taskIds", new JsonArray(List.copyOf(taskIds)));
        }
        return ret;
    }
}
