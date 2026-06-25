package io.quarkiverse.mcp.server.http.runtime;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
import io.quarkiverse.mcp.server.runtime.Messages;
import io.quarkiverse.mcp.server.runtime.Subscription;
import io.quarkiverse.mcp.server.runtime.TrafficListeners;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;

class StreamableHttpMcpConnection extends McpConnectionBase {

    private static final Logger LOG = Logger.getLogger(StreamableHttpMcpConnection.class);

    private volatile List<SseStream> sseStreams;

    StreamableHttpMcpConnection(String id, McpServerRuntimeConfig serverConfig, String serverName,
            TrafficListeners trafficListeners) {
        this(id, serverConfig, serverName, trafficListeners, false);
    }

    StreamableHttpMcpConnection(String id, McpServerRuntimeConfig serverConfig, String serverName,
            TrafficListeners trafficListeners, boolean transientConnection) {
        super(id, Objects.requireNonNull(serverConfig), serverName, trafficListeners, transientConnection);
    }

    void addSse(SseStream sse) {
        List<SseStream> streams = this.sseStreams;
        if (streams == null) {
            synchronized (this) {
                streams = this.sseStreams;
                if (streams == null) {
                    streams = new CopyOnWriteArrayList<>();
                    this.sseStreams = streams;
                }
            }
        }
        streams.add(sse);
    }

    boolean removeSse(String id) {
        List<SseStream> streams = this.sseStreams;
        if (streams == null) {
            return false;
        }
        return streams.removeIf(e -> e.id().equals(id));
    }

    @Override
    public boolean close() {
        List<SseStream> streams = this.sseStreams;
        if (streams != null) {
            streams.clear();
        }
        return super.close();
    }

    @Override
    public Future<Void> send(JsonObject message) {
        if (message == null) {
            return Future.succeededFuture();
        }
        List<SseStream> streams = this.sseStreams;
        if (streams == null) {
            Object id = Messages.getId(message);
            String method = message.getString("method");
            LOG.debugf("Discarding message [id=%s,method=%s] - no SSE streams open yet", id, method);
            return Future.succeededFuture();
        }
        // Pick the first legacy stream (no subscription filter) for non-subscription messages
        for (SseStream s : streams) {
            if (s.subscription() == null) {
                messageSent(message);
                return s.sendEvent("message", message.encode());
            }
        }
        Object id = Messages.getId(message);
        String method = message.getString("method");
        LOG.debugf("Discarding message [id=%s,method=%s] - no SSE streams open yet", id, method);
        return Future.succeededFuture();
    }

    @Override
    protected void deliverSubscriptionNotification(JsonObject notification, Subscription subscription) {
        List<SseStream> streams = this.sseStreams;
        if (streams == null) {
            LOG.debugf("Subscription stream not found, notification dropped [%s]", id());
            return;
        }
        String targetId = String.valueOf(subscription.subscriptionId());
        for (SseStream stream : streams) {
            if (stream.subscription() != null
                    && stream.id().equals(targetId)) {
                messageSent(notification);
                stream.sendEvent("message", notification.encode());
                return;
            }
        }
        LOG.debugf("Subscription stream [%s] not found, notification dropped [%s]", targetId, id());
    }

    record SseStream(String id, HttpServerResponse response, Subscription subscription) {

        SseStream(String id, HttpServerResponse response) {
            this(id, response, null);
        }

        public SseStream {
            if (id == null) {
                throw new IllegalArgumentException("id must not be null");
            }
            if (response == null) {
                throw new IllegalArgumentException("response must not be null");
            }
        }

        public Future<Void> sendEvent(String name, String data) {
            // "write" is async and synchronized over http connection, and should be thread-safe
            return response.write("event: " + name + "\ndata: " + data + "\n\n");
        }

    }

}
