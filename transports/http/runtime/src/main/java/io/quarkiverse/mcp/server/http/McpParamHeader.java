package io.quarkiverse.mcp.server.http;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.quarkiverse.mcp.server.Tool;

/**
 * Designates a {@link Tool} method parameter to be mirrored as an HTTP header ({@code Mcp-Param-{value}}) when using the
 * Streamable HTTP transport.
 * <p>
 * This enables network intermediaries (load balancers, gateways, firewalls) to route and inspect requests based on parameter
 * values without parsing the JSON-RPC request body.
 * <p>
 * The annotated parameter must be of type {@code String}, {@code int}/{@code Integer}, {@code long}/{@code Long},
 * or {@code boolean}/{@code Boolean}.
 *
 * <pre>
 * &#64;Tool
 * String query(&#64;McpParamHeader("Region") String region, String value) {
 *     // ...
 * }
 * </pre>
 *
 * @see Tool
 */
@Retention(RUNTIME)
@Target(ElementType.PARAMETER)
public @interface McpParamHeader {

    /**
     * The header name portion used to construct the HTTP header {@code Mcp-Param-{value}}.
     * <p>
     * Must be a non-empty, valid HTTP field-name token as defined by RFC 9110 Section 5.1.
     */
    String value();

}
