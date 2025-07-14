package io.quarkiverse.mcp.server.sse.client;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import io.vertx.core.MultiMap;

public abstract class SseClient {

    private static final Logger LOG = Logger.getLogger(SseClient.class);

    protected final URI connectUri;

    public SseClient(URI uri) {
        this.connectUri = uri;
    }

    protected abstract void process(SseEvent event);

    protected void connectionFailed() {
        // No-op
    }

    public CompletableFuture<HttpResponse<Void>> connect() {
        return connect(null, Map.of());
    }

    public CompletableFuture<HttpResponse<Void>> connect(Map<String, String> headers) {
        return connect(null, headers);
    }

    protected HttpRequest buildConnectRequest(MultiMap headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(connectUri)
                .version(Version.HTTP_1_1)
                .header("Accept", "text/event-stream")
                .GET();
        headers.forEach(builder::header);
        return builder.build();
    }

    public CompletableFuture<HttpResponse<Void>> connect(HttpClient client, Map<String, String> headers) {
        MultiMap h = MultiMap.caseInsensitiveMultiMap();
        headers.forEach(h::add);
        return connect(client, h);
    }

    public CompletableFuture<HttpResponse<Void>> connect(HttpClient client, MultiMap headers) {
        if (client == null) {
            client = HttpClient.newHttpClient();
        }
        HttpRequest request = buildConnectRequest(headers);

        return client.sendAsync(request, BodyHandlers.fromLineSubscriber(new SseEventSubscriber(this::process)))
                .exceptionally(e -> {
                    if (e instanceof CompletionException) {
                        e = e.getCause();
                    }
                    if (e instanceof ConnectException ce) {
                        LOG.errorf(ce.getCause(), "Connection failed: %s", connectUri);
                        connectionFailed();
                    } else {
                        Throwable root = getRootCause(e);
                        if (!(root instanceof EOFException)) {
                            LOG.error(e);
                        }
                    }
                    return null;
                });
    }

    public static class SseEventSubscriber implements Flow.Subscriber<String> {

        private Flow.Subscription subscription;

        private String event = "message";
        private StringBuilder dataBuffer = new StringBuilder();

        private final Consumer<SseEvent> eventProcessor;

        public SseEventSubscriber(Consumer<SseEvent> eventProcessor) {
            this.eventProcessor = eventProcessor;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(String line) {
            LOG.debugf("Received next line:\n%s", line);
            if (line.startsWith(":")) {
                // Skip comments
            } else if (line.isBlank()) {
                // Flush
                eventProcessor.accept(new SseEvent(event, dataBuffer.toString().strip()));
                event = "message";
                dataBuffer = new StringBuilder();
            } else if (line.contains(":")) {
                int colon = line.indexOf(":");
                String field = line.substring(0, colon).strip();
                String value = line.substring(colon + 1).strip();
                handleField(field, value);
            } else {
                // The whole line is the field name
                handleField(line, "");
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable t) {
            Throwable root = getRootCause(t);
            if (root instanceof IOException io) {
                if (io.getMessage().contains("Request cancelled")) {
                    // This is ok - no need to log anything
                    return;
                }
            }
            if (root instanceof EOFException) {
                // EOF is not a problem
                return;
            }
            LOG.errorf(t, "Error in SSE stream");
        }

        @Override
        public void onComplete() {
            LOG.debug("SSE stream complete");
        }

        private void handleField(String field, String value) {
            switch (field) {
                case "event" -> event = value;
                case "data" -> dataBuffer.append(value).append("\n");
            }
        }
    }

    public record SseEvent(String name, String data) {
    }

    private static Throwable getRootCause(Throwable exception) {
        final List<Throwable> chain = new ArrayList<>();
        Throwable curr = exception;
        while (curr != null && !chain.contains(curr)) {
            chain.add(curr);
            curr = curr.getCause();
        }
        return chain.isEmpty() ? null : chain.get(chain.size() - 1);
    }
}
