package io.quarkiverse.mcp.server;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.quarkiverse.mcp.server.McpServer.McpServers;

/**
 * Binds a feature to a specific server configuration.
 * <p>
 * A feature can be bound to multiple server configurations. The set of bindings includes all values
 * declared on the feature method and all values defined on the declaring class of the feature.
 * <p>
 * In versions 1.10 and lower, it was not possible to bind a feature to multiple servers. Also a binding declared on a class was
 * applied to all features defined in the class that did not declare the annotation themselves. However, this behavior makes no
 * sense anymore. Instead, all values declared on a class are included in the set of servers for a feature. If you need to
 * revert to the previous behavior you can set the {@code quarkus.mcp.server.support-multi-server-bindings} configuration
 * property to {@code false}. Then the previous rules will apply and multiple server bindings will then result in a
 * build failure. If the property is not set explicitly and an ambiguous pattern is detected (a feature method and its
 * declaring class both declare different {@code @McpServer} values), the build will fail with an error explaining the
 * options.
 *
 * @see Tool
 * @see Prompt
 * @see Resource
 * @see ResourceTemplate
 */
@Retention(RUNTIME)
@Target({ METHOD, TYPE })
@Repeatable(McpServers.class)
public @interface McpServer {

    /**
     * Constant value for the name of the default server.
     */
    String DEFAULT = "<default>";

    /**
     * The name of the server.
     */
    String value();

    @Retention(RUNTIME)
    @Target({ METHOD, TYPE })
    @interface McpServers {

        McpServer[] value();

    }

}
