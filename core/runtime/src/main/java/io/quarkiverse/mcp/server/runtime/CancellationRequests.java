package io.quarkiverse.mcp.server.runtime;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.RequestId;
import io.vertx.core.json.JsonObject;

/**
 * Tracks cancellation state per (connection, request) pair.
 * <p>
 * Uses a single {@link ConcurrentHashMap} with per-key {@link ConcurrentHashMap#compute(Object, BiFunction)} calls
 * for atomicity — no global lock needed.
 */
@Singleton
public class CancellationRequests {

    private static final Logger LOG = Logger.getLogger(CancellationRequests.class);

    private final ConcurrentMap<CancellationRequestKey, CancellationState> states;

    public CancellationRequests() {
        this.states = new ConcurrentHashMap<>();
    }

    /**
     * @return the cancellation reason if cancelled, {@code null} if not cancelled
     */
    Optional<String> get(McpConnection connection, RequestId requestId) {
        CancellationState state = states.get(new CancellationRequestKey(connection.id(), requestId));
        if (state instanceof Cancelled c) {
            return c.reason;
        }
        return null;
    }

    /**
     * Marks the request as cancelled and runs any pending actions outside of {@code compute()}.
     *
     * @return {@code true} if the request was newly cancelled, {@code false} if already cancelled
     */
    boolean add(McpConnection connection, RequestId requestId, String reason) {
        CancellationRequestKey key = new CancellationRequestKey(connection.id(), requestId);
        AtomicBoolean alreadyCancelled = new AtomicBoolean();
        // Captured inside compute(), executed after — actions must not run under the bucket lock
        List<Consumer<Optional<String>>> pendingActions = new ArrayList<>();
        Optional<String> optReason = Optional.ofNullable(reason);

        states.compute(key, new BiFunction<>() {
            @Override
            public CancellationState apply(CancellationRequestKey k, CancellationState current) {
                if (current instanceof Cancelled) {
                    alreadyCancelled.set(true);
                    return current;
                }
                if (current instanceof Pending p) {
                    pendingActions.addAll(p.actions);
                }
                return new Cancelled(optReason);
            }
        });

        if (alreadyCancelled.get()) {
            return false;
        }

        for (Consumer<Optional<String>> action : pendingActions) {
            try {
                action.accept(optReason);
            } catch (Exception e) {
                LOG.errorf(e, "Error executing cancellation action");
            }
        }
        return true;
    }

    /**
     * Registers an action to run when the request is cancelled.
     * If already cancelled, the action runs immediately (outside of {@code compute()}).
     */
    void subscribe(McpConnection connection, RequestId requestId, Consumer<Optional<String>> action) {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        CancellationRequestKey key = new CancellationRequestKey(connection.id(), requestId);
        // Captured inside compute() — the reason (if already cancelled) and runNow flag
        AtomicBoolean runNow = new AtomicBoolean();
        Optional<String>[] reasonHolder = new Optional[] { Optional.empty() };

        states.compute(key, new BiFunction<>() {
            @Override
            public CancellationState apply(CancellationRequestKey k, CancellationState current) {
                if (current instanceof Cancelled c) {
                    runNow.set(true);
                    reasonHolder[0] = c.reason;
                    return current;
                }
                if (current instanceof Pending p) {
                    p.actions.add(action);
                    return current;
                }
                List<Consumer<Optional<String>>> actions = new ArrayList<>();
                actions.add(action);
                return new Pending(actions);
            }
        });

        if (runNow.get()) {
            action.accept(reasonHolder[0]);
        }
    }

    /**
     * Removes the state for a completed request.
     */
    void remove(McpConnection connection, JsonObject request) {
        states.remove(new CancellationRequestKey(connection.id(), new RequestId(Messages.getId(request))));
    }

    /**
     * Removes all entries for a closed connection.
     */
    void connectionClosed(String connectionId) {
        Iterator<CancellationRequestKey> it = states.keySet().iterator();
        while (it.hasNext()) {
            if (it.next().connectionId.equals(connectionId)) {
                it.remove();
            }
        }
    }

    private sealed interface CancellationState permits Pending, Cancelled {
    }

    private record CancellationRequestKey(String connectionId, RequestId requestId) {
    }

    /**
     * Not yet cancelled — holds actions to run when cancellation arrives.
     */
    private static final class Pending implements CancellationState {
        final List<Consumer<Optional<String>>> actions;

        Pending(List<Consumer<Optional<String>>> actions) {
            this.actions = actions;
        }
    }

    /**
     * Cancellation was requested — late subscribers run immediately.
     */
    private static final class Cancelled implements CancellationState {
        final Optional<String> reason;

        Cancelled(Optional<String> reason) {
            this.reason = reason;
        }
    }
}
