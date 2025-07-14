package io.quarkiverse.mcp.server.sse.runtime;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;

class StreamableHttpMcpConnection extends McpConnectionBase {

    private static final Logger LOG = Logger.getLogger(StreamableHttpMcpConnection.class);

    private final List<SubsidiarySse> sseStreams;

    StreamableHttpMcpConnection(String id, McpServerRuntimeConfig serverConfig) {
        super(id, serverConfig);
        this.sseStreams = new CopyOnWriteArrayList<>();
    }

    void addSse(SubsidiarySse sse) {
        sseStreams.add(sse);
    }

    boolean removeSse(String id) {
        return sseStreams.removeIf(e -> e.id().equals(id));
    }

    @Override
    public boolean close() {
        sseStreams.clear();
        return super.close();
    }

    @Override
    public Future<Void> send(JsonObject message) {
        if (message == null) {
            return Future.succeededFuture();
        }
        SubsidiarySse sse = null;
        if (!sseStreams.isEmpty()) {
            try {
                sse = sseStreams.get(0);
            } catch (IndexOutOfBoundsException expected) {
            }
        }
        if (sse == null) {
            String method = message.getString("method");
            LOG.warnf("Discarding message [%s] - no 'subsidiary' SSE streams open yet", method);
            return Future.succeededFuture();
        } else {
            if (trafficLogger != null) {
                trafficLogger.messageSent(message, this);
            }
            return sse.sendEvent("message", message.encode());
        }
    }

    record SubsidiarySse(String id, HttpServerResponse response) {

        public SubsidiarySse {
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
