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
     * @param serverName
     * @return the notification callback of the given type with the given name bound to the given server, or {@code null}
     * @see McpServer
     */
    NotificationInfo getNotification(Notification.Type type, String name, String serverName);

    /**
     * For backwards compatibility, this method does not default to the {@link McpServer#DEFAULT} server configuration.
     * Instead, it searches across all servers and throws an exception if the type and name combination is ambiguous.
     *
     * @param type
     * @param name
     * @return the notification callback of the given type with the given name, or {@code null}
     * @throws IllegalStateException if multiple notifications with the given type and name exist on different servers
     * @see #getNotification(Notification.Type, String, String)
     */
    NotificationInfo getNotification(Notification.Type type, String name);

    /**
     * The name must be unique within a server configuration. A notification with the same name can exist on different servers.
     *
     * @param name
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
     * Notification info.
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

        /**
         *
         * @return the notification info
         * @throws IllegalArgumentException if a notification with the given name and type already exists for the same server
         *         configuration
         */
        @Override
        NotificationInfo register();

    }

    interface NotificationArguments extends FeatureArguments {

    }

}
