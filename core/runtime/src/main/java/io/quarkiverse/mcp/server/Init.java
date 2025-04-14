package io.quarkiverse.mcp.server;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotate a business method of a CDI bean that will be called asynchronously when an MCP client sends the
 * {@code notifications/initialized} message.
 * <p>
 * The annotated method must either return {@code void} or {@code Uni<Void>}. It may accept the {@link McpConnection}
 * and {@link McpLog} parameters.
 * 
 * @see McpConnection#initialRequest()
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface Init {

}
