package io.quarkiverse.mcp.server;

import io.quarkiverse.mcp.server.ResourceTemplateManager.ResourceTemplateInfo;

/**
 * Any CDI bean that implements this interface is used to determine the set of visible/accesible resource templates for a
 * specific MCP client.
 * <p>
 * Filters should be fast and efficient, and should never block the current thread (read data from a socket, write data to
 * disk, etc.). If a filter throws an unchecked exception then its execution is ignored and the next filter is applied.
 * <p>
 * Multiple filters are sorted by {@link io.quarkus.arc.InjectableBean#getPriority()} and executed sequentially. Higher priority
 * is executed first. Only features that match all the filters are visible/accesible.
 */
public interface ResourceTemplateFilter {

    /**
     * Returns {@code true} if the given resource template should be considered visible/accesible for a specific MCP client.
     * <p>
     * Note that the container always calls {@link ResourceTemplateFilter#test(ResourceTemplateInfo, FilterContext)} that
     * delegates to {@link #test(ResourceTemplateInfo, McpConnection)} by default.
     *
     * @param resourceTemplate (must not be {@code null})
     * @param connection (must not be {@code null})
     * @return {@code true} if visible/accesible, {@code false} otherwise
     */
    default boolean test(ResourceTemplateInfo resourceTemplate, McpConnection connection) {
        return true;
    }

    /**
     * Returns {@code true} if the given resource template should be considered visible/accesible for a specific MCP client.
     *
     * @param resourceTemplate (must not be {@code null})
     * @param context (must not be {@code null})
     * @return {@code true} if visible/accesible, {@code false} otherwise
     */
    default boolean test(ResourceTemplateInfo resourceTemplate, FilterContext context) {
        return test(resourceTemplate, context.connection());
    }

}
