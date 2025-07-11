package io.quarkiverse.mcp.server.test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;

import io.quarkiverse.mcp.server.test.McpAssured.Snapshot;
import io.vertx.core.json.JsonObject;

final class McpClientState {

    final AtomicInteger requestIdGenerator;
    final List<JsonObject> requests;
    final List<JsonObject> responses;
    final List<JsonObject> notifications;

    McpClientState() {
        this.requestIdGenerator = new AtomicInteger();
        this.requests = new CopyOnWriteArrayList<>();
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

    public JsonObject waitForResponse(JsonObject request) {
        int id = request.getInteger("id");
        Awaitility.await().until(() -> getResponse(id) != null);
        return getResponse(id);
    }

    public JsonObject getResponse(int id) {
        for (JsonObject r : responses) {
            if (r.getInteger("id") == id) {
                return r;
            }
        }
        return null;
    }

    public List<JsonObject> getRequests() {
        return List.copyOf(requests);
    }

    public List<JsonObject> getResponses() {
        return List.copyOf(responses);
    }

    public List<JsonObject> getNotifications() {
        return List.copyOf(notifications);
    }

    public void clearRequests() {
        requests.clear();
    }

    public Snapshot toSnapshot() {
        return new Snapshot(getRequests(), getResponses(), getNotifications());
    }

}
