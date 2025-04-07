package io.quarkiverse.mcp.server.test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.awaitility.Awaitility;

import io.quarkiverse.mcp.server.sse.client.SseClient;
import io.vertx.core.json.JsonObject;

public class McpSseClient extends SseClient {

    private final AtomicInteger requestIdGenerator;

    private final List<SseEvent> allEvents;
    private final List<JsonObject> requests;
    private final List<JsonObject> responses;
    private final List<JsonObject> notifications;

    private final AtomicReference<Consumer<JsonObject>> requestConsumer = new AtomicReference<>();

    public McpSseClient(URI uri) {
        super(uri);
        this.requestIdGenerator = new AtomicInteger();
        this.allEvents = new CopyOnWriteArrayList<>();
        this.requests = new CopyOnWriteArrayList<>();
        this.responses = new CopyOnWriteArrayList<>();
        this.notifications = new CopyOnWriteArrayList<>();
    }

    public void setRequestConsumer(Consumer<JsonObject> value) {
        this.requestConsumer.set(value);
    }

    public int nextRequestId() {
        return requestIdGenerator.incrementAndGet();
    }

    public JsonObject waitForLastResponse() {
        int lastId = requestIdGenerator.get();
        if (lastId == 0) {
            return null;
        }
        Awaitility.await().until(() -> responses.stream().anyMatch(r -> r.getInteger("id") == lastId));
        return responses.stream().filter(r -> r.getInteger("id") == lastId).findFirst().orElseThrow();
    }

    public List<JsonObject> waitForNotifications(int count) {
        Awaitility.await().until(() -> notifications.size() >= count);
        return notifications;
    }

    public List<JsonObject> waitForRequests(int count) {
        Awaitility.await().until(() -> requests.size() >= count);
        return requests;
    }

    public List<JsonObject> waitForResponses(int count) {
        Awaitility.await().until(() -> responses.size() >= count);
        return responses;
    }

    public void clearRequests() {
        requests.clear();
    }

    public SseEvent waitForFirstEvent() {
        Awaitility.await().until(() -> !allEvents.isEmpty());
        return allEvents.get(0);
    }

    @Override
    protected void process(SseEvent event) {
        allEvents.add(event);
        if ("message".equals(event.name())) {
            JsonObject json = new JsonObject(event.data());
            if (json.containsKey("id")) {
                if (json.containsKey("result") || json.containsKey("error")) {
                    responses.add(json);
                } else {
                    // Request from the server
                    requests.add(json);
                    Consumer<JsonObject> c = requestConsumer.get();
                    if (c != null) {
                        c.accept(json);
                    }
                }
            } else {
                notifications.add(json);
            }
        }
    }

}
