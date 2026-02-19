package io.quarkiverse.mcp.server;

import java.util.Collection;

/**
 * Stores MCP connections. The default implementation uses an in-memory {@link java.util.concurrent.ConcurrentHashMap}.
 * <p>
 * A custom implementation can be provided as a CDI bean. If a CDI bean implementing this interface is found, it is used instead
 * of the default implementation.
 * <p>
 * Implementations must be thread-safe.
 */
public interface ConnectionStore {

    /**
     * Stores the given connection.
     *
     * @param connection the connection to store (not {@code null})
     */
    void put(McpConnection connection);

    /**
     * Returns the connection with the given identifier, or {@code null} if not found.
     *
     * @param id the connection identifier (not {@code null})
     * @return the connection, or {@code null}
     */
    McpConnection get(String id);

    /**
     * Removes and returns the connection with the given identifier, or {@code null} if not found.
     *
     * @param id the connection identifier (not {@code null})
     * @return the removed connection, or {@code null}
     */
    McpConnection remove(String id);

    /**
     * Returns {@code true} if a connection with the given identifier exists.
     *
     * @param id the connection identifier (not {@code null})
     * @return {@code true} if the connection exists
     */
    default boolean contains(String id) {
        return get(id) != null;
    }

    /**
     * Returns a snapshot of all stored connections.
     *
     * @return an unmodifiable collection of connections
     */
    Collection<McpConnection> connections();

    /**
     * Returns the number of stored connections.
     *
     * @return the number of connections
     */
    default int size() {
        return connections().size();
    }

}
