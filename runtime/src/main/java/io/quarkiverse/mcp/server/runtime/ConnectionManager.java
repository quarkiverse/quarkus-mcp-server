package io.quarkiverse.mcp.server.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.inject.Singleton;

@Singleton
class ConnectionManager {

    private ConcurrentMap<String, McpConnectionImpl> connections = new ConcurrentHashMap<>();

    McpConnectionImpl get(String id) {
        return connections.get(id);
    }

    void add(McpConnectionImpl connection) {
        connections.put(connection.id(), connection);
    }

    boolean remove(String id) {
        return connections.remove(id) != null;
    }

}
