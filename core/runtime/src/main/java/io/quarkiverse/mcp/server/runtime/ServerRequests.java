package io.quarkiverse.mcp.server.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.ElicitationCompletion;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

@Singleton
public class ServerRequests implements ElicitationCompletion {

    private static final Logger LOG = Logger.getLogger(ServerRequests.class);

    // Expired pending elicitations are checked every minute.
    // This means that the effective minimum completion timeout is approximately one minute.
    private static final long PENDING_ELICITATION_CHECK_INTERVAL_MS = 60_000;

    // request id -> response handler
    private final ConcurrentMap<Long, ResponseHandler> responseHandlers;

    private final RequestIdGenerator idGenerator;

    private final McpServersRuntimeConfig config;

    private final ConnectionManager connectionManager;

    private final ConcurrentMap<String, PendingElicitation> pendingElicitations = new ConcurrentHashMap<>();

    ServerRequests(McpServersRuntimeConfig config, RequestIdGenerator idGenerator,
            ConnectionManager connectionManager, Vertx vertx) {
        this.responseHandlers = new ConcurrentHashMap<>();
        this.idGenerator = idGenerator;
        this.config = config;
        this.connectionManager = connectionManager;
        vertx.setPeriodic(PENDING_ELICITATION_CHECK_INTERVAL_MS, id -> {
            if (!pendingElicitations.isEmpty()) {
                pendingElicitations.values().removeIf(PendingElicitation::isExpired);
            }
        });
    }

    public boolean hasResponseHandler(long id) {
        return responseHandlers.containsKey(id);
    }

    Long newRequest(Consumer<JsonObject> responseConsumer) {
        Long nextId = idGenerator.nextId();
        responseHandlers.put(nextId, new ResponseHandler(Instant.now(), responseConsumer));
        return nextId;
    }

    boolean removeResponseHandler(long id) {
        if (responseHandlers.remove(id) != null) {
            LOG.debugf("Removed response handler for %s", id);
            return true;
        }
        return false;
    }

    Future<Void> handleResponse(Object id, JsonObject message) {
        if (id == null) {
            LOG.debugf("Discard client response with no id: %s", message);
        } else {
            ResponseHandler handler = responseHandlers.remove(coerceResponseId(id));
            if (handler == null) {
                // Discard all responses for which a handler is not found
                // including pong responses
                LOG.debugf("Handler not found - discard client response with id %s", id);
            } else {
                try {
                    handler.operation().accept(message);
                } catch (Throwable e) {
                    LOG.errorf(e, "Unable to process the response with id %s", id);
                    return Future.failedFuture(e);
                }
            }
        }
        return Future.succeededFuture();
    }

    private Long coerceResponseId(Object id) {
        if (id instanceof Long longId) {
            return longId;
        } else if (id instanceof Integer intId) {
            return intId.longValue();
        } else if (id instanceof String strId) {
            return Long.parseLong(strId);
        }
        throw new IllegalArgumentException("Unsupported response identifier: " + id);
    }

    Duration getSamplingTimeout(String serverName) {
        return config.servers().get(serverName).sampling().defaultTimeout();
    }

    Duration getRootsTimeout(String serverName) {
        return config.servers().get(serverName).roots().defaultTimeout();
    }

    Duration getElicitationTimeout(String serverName) {
        return config.servers().get(serverName).elicitation().defaultTimeout();
    }

    Duration getElicitationCompletionTimeout(String serverName) {
        return config.servers().get(serverName).elicitation().defaultCompletionTimeout();
    }

    void registerElicitation(String elicitationId, String connectionId, Duration completionTimeout) {
        Instant expiresAt = completionTimeout.isNegative() || completionTimeout.isZero()
                ? Instant.MAX
                : Instant.now().plus(completionTimeout);
        pendingElicitations.put(elicitationId, new PendingElicitation(connectionId, expiresAt));
        LOG.debugf("Registered pending elicitation %s for connection %s (expires at %s)",
                elicitationId, connectionId, expiresAt);
    }

    void removeElicitation(String elicitationId) {
        if (pendingElicitations.remove(elicitationId) != null) {
            LOG.debugf("Removed pending elicitation %s", elicitationId);
        }
    }

    public boolean hasPendingElicitation(String elicitationId) {
        return pendingElicitations.containsKey(elicitationId);
    }

    @Override
    public void send(String elicitationId) {
        PendingElicitation pending = pendingElicitations.remove(elicitationId);
        if (pending == null) {
            throw new IllegalArgumentException("No pending elicitation found for ID: " + elicitationId);
        }
        McpConnectionBase connection = connectionManager.get(pending.connectionId());
        if (connection == null) {
            LOG.warnf("Connection %s not found for elicitation completion %s", pending.connectionId(), elicitationId);
            return;
        }
        JsonObject params = new JsonObject().put("elicitationId", elicitationId);
        connection.send(Messages.newNotification(McpMethod.NOTIFICATIONS_ELICITATION_COMPLETE.jsonRpcName(), params));
        LOG.debugf("Sent elicitation completion notification for %s to connection %s",
                elicitationId, pending.connectionId());
    }

    private record PendingElicitation(String connectionId, Instant expiresAt) {

        boolean isExpired() {
            boolean expired = Instant.now().isAfter(expiresAt);
            if (expired) {
                LOG.debugf("Pending elicitation for connection %s expired", connectionId);
            }
            return expired;
        }
    }

    private record ResponseHandler(Instant creationTime, Consumer<JsonObject> operation) {

        public ResponseHandler {
            if (creationTime == null) {
                throw new IllegalArgumentException("creationTime must not be null");
            }
            if (operation == null) {
                throw new IllegalArgumentException("operation must not be null");
            }
        }

    }

}
