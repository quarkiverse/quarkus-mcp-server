package io.quarkiverse.mcp.server.runtime;

import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.ConnectionStore;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpConnectionEvent;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

@Singleton
public class ConnectionManager implements Iterable<McpConnectionBase> {

    private static final Logger LOG = Logger.getLogger(ConnectionManager.class);

    private final Vertx vertx;

    private final ResponseHandlers responseHandlers;

    private final Event<McpConnectionEvent> connectionEvent;

    private final ConnectionStore connectionStore;

    private final ConcurrentMap<String, Long> autoPingTimers = new ConcurrentHashMap<>();

    public ConnectionManager(Vertx vertx, ResponseHandlers responseHandlers, McpServersRuntimeConfig servers,
            McpMetadata metadata, Instance<McpMetrics> metrics, Event<McpConnectionEvent> connectionEvent,
            Instance<ConnectionStore> connectionStoreInstance) {
        this.vertx = vertx;
        this.responseHandlers = responseHandlers;
        this.connectionEvent = connectionEvent;
        this.connectionStore = connectionStoreInstance.isResolvable() ? connectionStoreInstance.get()
                : new InMemoryConnectionStore();
        // We use the minimal timeout divided by two to specify the delay to fire the check
        // For example, if there are two server configs; the first defines the timeout 10 mins and the second 30 mins,
        // then we fire the check every 5 mins
        long minConnectionIdleTimeout = findMinimalConnectionIdleTimeout(servers, metadata);
        if (minConnectionIdleTimeout > 0) {
            vertx.setPeriodic(minConnectionIdleTimeout / 2, new Handler<Long>() {
                @Override
                public void handle(Long event) {
                    for (McpConnection connection : connectionStore.connections()) {
                        if (connection instanceof McpConnectionBase base && base.isIdleTimeoutExpired()) {
                            LOG.debugf("Connection idle timeout expired [%s]", base.id());
                            remove(base.id());
                        }
                    }
                }
            });
        }
        if (metrics.isResolvable()) {
            metrics.get().createMcpConnectionsGauge(connectionStore, ConnectionStore::size);
        }
    }

    @Override
    public Iterator<McpConnectionBase> iterator() {
        return connectionStore.connections().stream()
                .filter(McpConnectionBase.class::isInstance)
                .map(McpConnectionBase.class::cast)
                .iterator();
    }

    public boolean has(String id) {
        return connectionStore.contains(id);
    }

    public McpConnectionBase get(String id) {
        McpConnection connection = connectionStore.get(id);
        return connection instanceof McpConnectionBase base ? base.touch() : null;
    }

    public void add(McpConnectionBase connection) {
        if (connection.autoPingInterval().isPresent()) {
            Long timerId = vertx.setPeriodic(connection.autoPingInterval().get().toMillis(), new Handler<Long>() {
                @Override
                public void handle(Long timerId) {
                    connection.send(Messages.newPing(responseHandlers.nextId()));
                }
            });
            autoPingTimers.put(connection.id(), timerId);
        }
        connectionStore.put(connection);
    }

    public boolean remove(String id) {
        McpConnection connection = connectionStore.remove(id);
        if (connection != null) {
            Long timerId = autoPingTimers.remove(id);
            if (timerId != null) {
                vertx.cancelTimer(timerId);
            }
            if (connection instanceof McpConnectionBase base) {
                base.close();
                fireEvent(base, McpConnectionEvent.Type.CLOSED);
            }
            return true;
        }
        return false;
    }

    public void fireInitializing(McpConnectionBase connection) {
        fireEvent(connection, McpConnectionEvent.Type.INITIALIZING);
    }

    public void fireInitialized(McpConnectionBase connection) {
        fireEvent(connection, McpConnectionEvent.Type.INITIALIZED);
    }

    private void fireEvent(McpConnectionBase connection, McpConnectionEvent.Type type) {
        try {
            connectionEvent.fireAsync(new McpConnectionEvent(connection, type));
        } catch (Exception e) {
            LOG.errorf(e, "Error firing connection event [type: %s, connectionId: %s]", type, connection.id());
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
