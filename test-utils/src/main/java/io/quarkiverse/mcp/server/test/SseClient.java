package io.quarkiverse.mcp.server.test;

import java.io.EOFException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;
import org.jboss.logging.Logger;

public class SseClient {

    private static final Logger LOG = Logger.getLogger(SseClient.class);

    private final URI testUri;

    private final AtomicInteger idGenerator;

    public final List<SseEvent> events;

    public SseClient(URI uri) {
        this.testUri = uri;
        this.idGenerator = new AtomicInteger();
        this.events = new CopyOnWriteArrayList<>();
    }

    public int nextId() {
        return idGenerator.incrementAndGet();
    }

    public SseEvent waitForFirstEvent() {
        nextId();
        Awaitility.await().until(() -> !events.isEmpty());
        return events.get(0);
    }

    public SseEvent waitForLastEvent() {
        int lastId = idGenerator.get();
        Awaitility.await().until(() -> events.size() >= lastId);
        return events.get(lastId - 1);
    }

    public void connect() {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(testUri)
                .version(Version.HTTP_1_1)
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        client.sendAsync(request, BodyHandlers.fromLineSubscriber(new SseEventSubscriber()))
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        LOG.infof("Connected to SSE stream: %s", testUri);
                    } else {
                        LOG.errorf("Failed to connect %s: %s", response.statusCode(), testUri);
                    }
                })
                .exceptionally(e -> {
                    Throwable root = getRootCause(e);
                    if (!(root instanceof EOFException)) {
                        LOG.error(e);
                    }
                    return null;
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
                events.add(new SseEvent(event, dataBuffer.toString()));
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
