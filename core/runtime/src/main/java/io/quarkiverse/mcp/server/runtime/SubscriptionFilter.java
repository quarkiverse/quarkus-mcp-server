package io.quarkiverse.mcp.server.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Captures the subset of notification types a client subscribed to via {@code subscriptions/listen}.
 *
 * @param extensionSubscriptions the subscriptions contributed by {@link SubscriptionFilterExtension}s
 */
public record SubscriptionFilter(boolean toolsListChanged, boolean promptsListChanged, boolean resourcesListChanged,
        Set<String> resourceSubscriptions, List<ExtensionSubscription> extensionSubscriptions) {

    public SubscriptionFilter(boolean toolsListChanged, boolean promptsListChanged, boolean resourcesListChanged,
            Set<String> resourceSubscriptions) {
        this(toolsListChanged, promptsListChanged, resourcesListChanged, resourceSubscriptions, List.of());
    }

    /**
     * A subscription contributed by a {@link SubscriptionFilterExtension}.
     *
     * @param filterName the name of the array field in the {@code notifications} object
     * @param notificationMethod the JSON-RPC method of the routed notifications
     * @param keys the accepted keys
     */
    public record ExtensionSubscription(String filterName, String notificationMethod, Set<String> keys) {
    }

    public static SubscriptionFilter parse(JsonObject notifications) {
        return parse(notifications, List.of(), null);
    }

    /**
     * @param notifications the {@code notifications} object of the {@code subscriptions/listen} request
     * @param extensions the extensions to consult
     * @param mcpRequest the request
     * @return the filter
     * @throws io.quarkiverse.mcp.server.McpException if an extension rejects the request
     */
    public static SubscriptionFilter parse(JsonObject notifications, List<SubscriptionFilterExtension> extensions,
            McpRequest mcpRequest) {
        boolean tools = notifications.getBoolean("toolsListChanged", false);
        boolean prompts = notifications.getBoolean("promptsListChanged", false);
        boolean resources = notifications.getBoolean("resourcesListChanged", false);
        Set<String> resourceSubs = parseStrings(notifications, "resourceSubscriptions");
        List<ExtensionSubscription> extensionSubs = List.of();
        if (!extensions.isEmpty()) {
            extensionSubs = new ArrayList<>();
            for (SubscriptionFilterExtension extension : extensions) {
                Set<String> keys = parseStrings(notifications, extension.filterName());
                if (!keys.isEmpty()) {
                    Set<String> accepted = extension.accept(keys, mcpRequest);
                    if (accepted != null && !accepted.isEmpty()) {
                        extensionSubs.add(new ExtensionSubscription(extension.filterName(), extension.notificationMethod(),
                                Set.copyOf(accepted)));
                    }
                }
            }
            extensionSubs = List.copyOf(extensionSubs);
        }
        return new SubscriptionFilter(tools, prompts, resources, resourceSubs, extensionSubs);
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
     * @param notificationMethod the JSON-RPC method of the notification
     * @param key the resource URI for {@code notifications/resources/updated}, the key of an extension subscription (e.g.
     *        a task id), or {@code null}
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
            default -> matchesExtension(notificationMethod, key);
        };
    }

    private boolean matchesExtension(String notificationMethod, String key) {
        if (key == null) {
            return false;
        }
        for (ExtensionSubscription sub : extensionSubscriptions) {
            if (sub.notificationMethod().equals(notificationMethod) && sub.keys().contains(key)) {
                return true;
            }
        }
        return false;
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
        for (ExtensionSubscription sub : extensionSubscriptions) {
            ret.put(sub.filterName(), new JsonArray(List.copyOf(sub.keys())));
        }
        return ret;
    }
}
