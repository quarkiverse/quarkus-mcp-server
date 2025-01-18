package io.quarkiverse.mcp.server.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;

@Singleton
public class ConnectionManager {

    @Inject
    Vertx vertx;

    private final ConcurrentMap<String, ConnectionTimerId> connections = new ConcurrentHashMap<>();

    // TODO we might need to extract this in a global component in the future
    private final AtomicInteger idGenerator = new AtomicInteger();

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
                    connection.send(Messages.newPing(idGenerator.incrementAndGet()));
                }
            });
        }
        connections.put(connection.id(), new ConnectionTimerId(connection, timerId));

    }

    public boolean remove(String id) {
        ConnectionTimerId connection = connections.remove(id);
        if (connection != null) {
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
