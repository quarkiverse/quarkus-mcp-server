package io.quarkiverse.mcp.server.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

@Singleton
public class ResponseHandlers {

    private static final Logger LOG = Logger.getLogger(ResponseHandlers.class);

    // TODO implement a cleanup timer that will remove "outdated" handlers
    // request id -> response handler
    private final ConcurrentMap<Long, Consumer<JsonObject>> handlers;

    private final AtomicLong idGenerator;

    ResponseHandlers() {
        this.handlers = new ConcurrentHashMap<>();
        this.idGenerator = new AtomicLong();
    }

    Long nextId() {
        return idGenerator.incrementAndGet();
    }

    Long newRequest(Consumer<JsonObject> responseConsumer) {
        Long nextId = nextId();
        handlers.put(nextId, responseConsumer);
        return nextId;
    }

    Future<Void> handleResponse(Object id, JsonObject message) {
        if (id == null) {
            LOG.debugf("Discard client response with no id: %s", message);
        } else {
            Consumer<JsonObject> c = handlers.remove(coerceResponseId(id));
            if (c == null) {
                // Discard all responses for which a consumer is not defined
                // including pong responses
                LOG.debugf("Discard client response: %s", message);
            } else {
                try {
                    c.accept(message);
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

}
