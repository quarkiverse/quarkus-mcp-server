package io.quarkiverse.mcp.server;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method of an {@link McpExtension} class as a handler for a custom top-level JSON-RPC method, such as
 * {@code skills/list}.
 * <p>
 * The method is dispatched like a built-in method (e.g. {@code tools/call}).
 * <p>
 * The method name declared by {@link #value()} must not collide with a built-in method name, nor with another extension method
 * bound to an overlapping set of servers (see {@link McpServer}).
 * <p>
 * An extension binds to servers as a unit: the {@link McpServer} bindings are declared on the {@link McpExtension} class and
 * apply to all of its methods. Declaring {@link McpServer} on an individual extension method is not allowed and fails the
 * build; use {@link McpConnection#serverName()} if a method needs to behave differently per server.
 *
 * @see McpExtension
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface McpExtensionMethod {

    /**
     * The JSON-RPC method name handled by this method, e.g. {@code skills/list}.
     *
     * @return the JSON-RPC method name
     */
    String value();

}
