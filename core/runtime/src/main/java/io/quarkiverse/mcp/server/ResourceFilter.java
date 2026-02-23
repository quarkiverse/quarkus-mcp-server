package io.quarkiverse.mcp.server;

import io.quarkiverse.mcp.server.ResourceManager.ResourceInfo;

/**
 * Any CDI bean that implements this interface is used to determine the set of visible/accessible resources for a specific MCP
 * client.
 * <p>
 * Filters should be fast and efficient, and should never block the current thread (read data from a socket, write data to
 * disk, etc.). If a filter throws an unchecked exception then its execution is ignored and the next filter is applied.
 * <p>
 * Multiple filters are sorted by {@link io.quarkus.arc.InjectableBean#getPriority()} and executed sequentially. Higher priority
 * is executed first. Only features that match all the filters are visible/accessible.
 */
public interface ResourceFilter {

    /**
     * Returns {@code true} if the given resource should be considered visible/accessible for a specific MCP client.
     * <p>
     * Note that the container always calls {@link ResourceFilter#test(ResourceInfo, FilterContext)} that delegates
     * to {@link #test(ResourceInfo, McpConnection)} by default.
     *
     * @param resource (must not be {@code null})
     * @param connection (must not be {@code null})
     * @return {@code true} if visible/accessible, {@code false} otherwise
     */
    default boolean test(ResourceInfo resource, McpConnection connection) {
        return true;
    }

    /**
     * Returns {@code true} if the given resource should be considered visible/accessible for a specific MCP client.
     *
     * @param resource (must not be {@code null})
     * @param context (must not be {@code null})
     * @return {@code true} if visible/accessible, {@code false} otherwise
     */
    default boolean test(ResourceInfo resource, FilterContext context) {
        return test(resource, context.connection());
    }

}
