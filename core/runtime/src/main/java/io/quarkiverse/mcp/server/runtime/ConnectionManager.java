package io.quarkiverse.mcp.server.runtime;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;

@Singleton
public class ConnectionManager implements Iterable<McpConnectionBase> {

    @Inject
    Vertx vertx;

    @Inject
    ResponseHandlers responseHandlers;

    private final ConcurrentMap<String, ConnectionTimerId> connections = new ConcurrentHashMap<>();

    @Override
    public Iterator<McpConnectionBase> iterator() {
        return connections.values().stream().map(ConnectionTimerId::connection).iterator();
    }

    public McpConnectionBase get(String id) {
        ConnectionTimerId connectionTimerId = connections.get(id);
        return connectionTimerId != null ? connectionTimerId.connection() : null;
    }

    public void add(McpConnectionBase connection) {
        Long timerId = null;
        if (connection.autoPingInterval().isPresent()) {
            timerId = vertx.setPeriodic(connection.autoPingInterval().get().toMillis(), new Handler<Long>() {
                @Override
                public void handle(Long timerId) {
                    connection.send(Messages.newPing(responseHandlers.nextId()));
                }
            });
        }
        connections.put(connection.id(), new ConnectionTimerId(connection, timerId));

    }

    public boolean remove(String id) {
        ConnectionTimerId connection = connections.remove(id);
        if (connection != null) {
            connection.connection().close();
            if (connection.timerId() != null) {
                vertx.cancelTimer(connection.timerId());
            }
            return true;
        }
        return false;
    }

    record ConnectionTimerId(McpConnectionBase connection, Long timerId) {
    }

}
