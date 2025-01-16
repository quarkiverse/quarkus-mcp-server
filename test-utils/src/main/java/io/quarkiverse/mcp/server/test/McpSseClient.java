package io.quarkiverse.mcp.server.test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;

import io.vertx.core.json.JsonObject;

public class McpSseClient extends SseClient {

    private final AtomicInteger requestIdGenerator;

    private final List<SseEvent> allEvents;
    private final List<JsonObject> responses;
    private final List<JsonObject> notifications;

    public McpSseClient(URI uri) {
        super(uri);
        this.requestIdGenerator = new AtomicInteger();
        this.allEvents = new CopyOnWriteArrayList<>();
        this.responses = new CopyOnWriteArrayList<>();
        this.notifications = new CopyOnWriteArrayList<>();
    }

    public int nextRequestId() {
        return requestIdGenerator.incrementAndGet();
    }

    public JsonObject waitForLastResponse() {
        int lastId = requestIdGenerator.get();
        if (lastId == 0) {
            return null;
        }
        Awaitility.await().until(() -> responses.size() >= lastId);
        return responses.get(lastId - 1);
    }

    public List<JsonObject> waitForNotifications(int count) {
        Awaitility.await().until(() -> notifications.size() >= count);
        return notifications;
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
                responses.add(json);
            } else {
                notifications.add(json);
            }
        }
    }

}
