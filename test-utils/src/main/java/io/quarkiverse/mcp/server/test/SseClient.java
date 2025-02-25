package io.quarkiverse.mcp.server.test;

import java.io.EOFException;
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
import java.util.concurrent.Flow;

import org.jboss.logging.Logger;

public abstract class SseClient {

    private static final Logger LOG = Logger.getLogger(SseClient.class);

    private final URI testUri;

    public SseClient(URI uri) {
        this.testUri = uri;
    }

    protected abstract void process(SseEvent event);

    public CompletableFuture<HttpResponse<Void>> connect() {
        return connect(Map.of());
    }

    public CompletableFuture<HttpResponse<Void>> connect(Map<String, String> headers) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(testUri)
                .version(Version.HTTP_1_1)
                .header("Accept", "text/event-stream")
                .GET();
        headers.forEach(builder::header);
        HttpRequest request = builder.build();

        return client.sendAsync(request, BodyHandlers.fromLineSubscriber(new SseEventSubscriber()))
                .whenComplete((r, t) -> {
                    if (t != null) {
                        Throwable root = getRootCause(t);
                        if (!(root instanceof EOFException)) {
                            LOG.error(t);
                        }
                    } else {
                        if (r.statusCode() != 200) {
                            LOG.errorf("Failed to connect %s: %s", r.statusCode(), testUri);
                        }
                    }
                });
    }

    class SseEventSubscriber implements Flow.Subscriber<String> {

        private Flow.Subscription subscription;

        private String event = "message";
        private StringBuilder dataBuffer = new StringBuilder();

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
                process(new SseEvent(event, dataBuffer.toString()));
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
            if (!(root instanceof EOFException)) {
                LOG.errorf(t, "Error in SSE stream");
            }
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
