package io.quarkiverse.mcp.server;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotate a business method of a CDI bean that will be called when an MCP client sends a notification message,
 * such as {@code notifications/initialized}.
 * <p>
 * The annotated method must either return {@code void} or {@code Uni<Void>}.
 * <p>
 * The method may accept the following parameters:
 * <ul>
 * <li>{@link McpConnection}</li>
 * <li>{@link McpLog}</li>
 * <li>{@link Roots}</li>
 * </ul>
 *
 * @see McpConnection#initialRequest()
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface Notification {

    /**
     * @return the type of the notification
     */
    Type value();

    enum Type {
        /**
         * {@code notifications/initialized}
         */
        INITIALIZED,
        /**
         * {@code notifications/roots/list_changed}
         */
        ROOTS_LIST_CHANGED
    }
}
