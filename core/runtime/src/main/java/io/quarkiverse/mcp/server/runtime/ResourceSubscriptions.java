package io.quarkiverse.mcp.server.runtime;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.inject.Singleton;

/**
 * Connections subscribed to a resource via {@code resources/subscribe}.
 * <p>
 * A connection is recorded once per URI, however many times it subscribes. A URI without subscribers is removed, and
 * all subscriptions of a connection are removed when the connection is closed.
 */
@Singleton
public class ResourceSubscriptions {

    // uri -> subscribers (connection ids)
    private final ConcurrentMap<String, Set<String>> uriToSubscribers = new ConcurrentHashMap<>();

    void subscribe(String uri, String connectionId) {
        uriToSubscribers.compute(uri, (k, ids) -> {
            if (ids == null) {
                ids = ConcurrentHashMap.newKeySet();
            }
            ids.add(connectionId);
            return ids;
        });
    }

    void unsubscribe(String uri, String connectionId) {
        uriToSubscribers.computeIfPresent(uri, (k, ids) -> {
            ids.remove(connectionId);
            return ids.isEmpty() ? null : ids;
        });
    }

    /**
     * @return a snapshot of the connection ids subscribed to the given URI
     */
    public Set<String> subscribers(String uri) {
        Set<String> ids = uriToSubscribers.get(uri);
        return ids == null ? Set.of() : Set.copyOf(ids);
    }

    /**
     * Removes all subscriptions of a closed connection.
     */
    void connectionClosed(String connectionId) {
        for (String uri : uriToSubscribers.keySet()) {
            unsubscribe(uri, connectionId);
        }
    }

}
