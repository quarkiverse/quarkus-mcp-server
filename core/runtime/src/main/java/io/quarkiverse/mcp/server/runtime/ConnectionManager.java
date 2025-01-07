package io.quarkiverse.mcp.server.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.inject.Singleton;

@Singleton
public class ConnectionManager {

    private ConcurrentMap<String, McpConnectionBase> connections = new ConcurrentHashMap<>();

    public McpConnectionBase get(String id) {
        return connections.get(id);
    }

    public void add(McpConnectionBase connection) {
        connections.put(connection.id(), connection);
    }

    public boolean remove(String id) {
        return connections.remove(id) != null;
    }

}
