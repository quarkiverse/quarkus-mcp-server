package io.quarkiverse.mcp.server;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Binds features, such as tools, prompts, and resources, to a specific server configuration.
 * <p>
 * If declared on a class then all feature methods that do not declare this annotation share the specified server configuration.
 *
 * @see Tool
 * @see Prompt
 * @see Resource
 * @see ResourceTemplate
 */
@Retention(RUNTIME)
@Target({ METHOD, TYPE })
public @interface McpServer {

    /**
     * Constant value for the name of the default server.
     */
    String DEFAULT = "<default>";

    /**
     * The name of the server.
     */
    String value();

}
