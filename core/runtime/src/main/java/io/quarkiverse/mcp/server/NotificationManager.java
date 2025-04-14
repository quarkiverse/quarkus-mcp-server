package io.quarkiverse.mcp.server;

import java.util.function.Predicate;

import io.quarkiverse.mcp.server.NotificationManager.NotificationInfo;

/**
 * This manager can be used to register a new {@link Notification} callback programmatically.
 */
public interface NotificationManager extends FeatureManager<NotificationInfo> {

    /**
     *
     * @param type
     * @param name
     * @return the notification callback of the given type with the given name, or {@code null}
     */
    NotificationInfo getNotification(Notification.Type type, String name);

    /**
     *
     * @param name The name must be unique
     * @return a new definition builder
     * @see NotificationDefinition#register()
     */
    NotificationDefinition newNotification(String name);

    /**
     * Removes an init callback previously added with {@link #newNotification(String)}.
     *
     * @return the removed notification callback or {@code null} if no such notification callback existed
     */
    boolean removeNotification(Predicate<NotificationInfo> filter);

    /**
     * Notification callback info.
     */
    interface NotificationInfo extends FeatureManager.FeatureInfo {

        /**
         * @return the type of the notification
         */
        Notification.Type type();

    }

    /**
     * {@link NotificationInfo} definition.
     * <p>
     * This construct is not thread-safe and should not be reused.
     */
    interface NotificationDefinition
            extends FeatureDefinition<NotificationInfo, NotificationArguments, Void, NotificationDefinition> {

        /**
         *
         * @param type
         * @return self
         * @see Notification#value()
         */
        NotificationDefinition setType(Notification.Type type);

    }

    record NotificationArguments(McpConnection connection, McpLog log, Roots roots) {
    }

}
