package io.quarkiverse.mcp.server.runtime;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.quarkiverse.mcp.server.ConnectionStore;
import io.quarkiverse.mcp.server.McpConnection;

/**
 * Default in-memory implementation of {@link ConnectionStore} backed by a {@link ConcurrentHashMap}.
 */
class InMemoryConnectionStore implements ConnectionStore {

    private final ConcurrentMap<String, McpConnection> connections = new ConcurrentHashMap<>();

    @Override
    public void put(McpConnection connection) {
        connections.put(connection.id(), connection);
    }

    @Override
    public McpConnection get(String id) {
        return connections.get(id);
    }

    @Override
    public McpConnection remove(String id) {
        return connections.remove(id);
    }

    @Override
    public boolean contains(String id) {
        return connections.containsKey(id);
    }

    @Override
    public Collection<McpConnection> connections() {
        return List.copyOf(connections.values());
    }

    @Override
    public int size() {
        return connections.size();
    }

}
