package io.quarkiverse.mcp.server;

import java.util.Map;
import java.util.Optional;

/**
 * Provides the info included in the response to an `initialize` request.
 * <p>
 * This is a programmatic alternative to configuration properties like {@code quarkus.mcp.server.server-info.name}.
 * <p>
 * Implementations are CDI beans. Qualifiers are ignored. Implementations are sorted by
 * {@link io.quarkus.arc.InjectableBean#getPriority()}, the implementation with highest
 * priority is executed first and the first non-empty result is used.
 */
public interface InitialResponseInfo {

    /**
     * If no instructions are provided, configuration properties are used as a fallback.
     *
     * @param serverName
     * @return the instructions
     * @see McpServer
     */
    default Optional<String> instructions(String serverName) {
        return Optional.empty();
    }

    /**
     * If no implementation details are provided, configuration properties are used as a fallback.
     *
     * @param serverName
     * @return the implementation details for the MCP server
     * @see McpServer
     */
    default Optional<Implementation> implementation(String serverName) {
        return Optional.empty();
    }

    /**
     * @param serverName
     * @return the {@code _meta} part of the message
     * @see McpServer
     */
    default Optional<Map<MetaKey, Object>> meta(String serverName) {
        return Optional.empty();
    }

}
