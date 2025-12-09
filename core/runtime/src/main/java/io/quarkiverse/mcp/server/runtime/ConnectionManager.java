package io.quarkiverse.mcp.server.runtime;

import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

@Singleton
public class ConnectionManager implements Iterable<McpConnectionBase> {

    private static final Logger LOG = Logger.getLogger(ConnectionManager.class);

    private final Vertx vertx;

    private final ResponseHandlers responseHandlers;

    private final ConcurrentMap<String, ConnectionTimerId> connections = new ConcurrentHashMap<>();

    public ConnectionManager(Vertx vertx, ResponseHandlers responseHandlers, McpServersRuntimeConfig servers,
            McpMetadata metadata) {
        this.vertx = vertx;
        this.responseHandlers = responseHandlers;
        // We use the minimal timeout divided by two to specify the delay to fire the check
        // For example, if there are two server configs; the first defines the timeout 10 mins and the second 30 mins,
        // then we fire the check every 5 mins
        long minConnectionIdleTimeout = findMinimalConnectionIdleTimeout(servers, metadata);
        if (minConnectionIdleTimeout > 0) {
            vertx.setPeriodic(minConnectionIdleTimeout / 2, new Handler<Long>() {
                @Override
                public void handle(Long event) {
                    connections.values().removeIf(ConnectionTimerId::isIdleTimeoutExpired);
                }
            });
        }
    }

    @Override
    public Iterator<McpConnectionBase> iterator() {
        return connections.values().stream().map(ConnectionTimerId::connection).iterator();
    }

    public boolean has(String id) {
        return connections.containsKey(id);
    }

    public McpConnectionBase get(String id) {
        ConnectionTimerId connectionTimerId = connections.get(id);
        return connectionTimerId != null ? connectionTimerId.connection().touch() : null;
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

        boolean isIdleTimeoutExpired() {
            boolean ret = connection.isIdleTimeoutExpired();
            if (ret) {
                LOG.debugf("Connection idle timeout expired [%s]", connection.id());
            }
            return ret;
        }
    }

    private long findMinimalConnectionIdleTimeout(McpServersRuntimeConfig config, McpMetadata metadata) {
        Set<String> serverNames = new HashSet<>(metadata.serverNames());
        serverNames.addAll(config.servers().keySet());
        if (serverNames.isEmpty()) {
            return 0;
        }
        long min = Long.MAX_VALUE;
        for (String serverName : serverNames) {
            long timeout = config.servers().get(serverName).connectionIdleTimeout().toMillis();
            if (timeout < min) {
                min = timeout;
            }
        }
        return min;
    }

    public static String connectionId() {
        return Base64.getUrlEncoder().encodeToString(UUID.randomUUID().toString().getBytes());
    }

}
