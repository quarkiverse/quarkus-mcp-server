package io.quarkiverse.mcp.server.test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;

import io.quarkiverse.mcp.server.test.McpAssured.Snapshot;
import io.vertx.core.json.JsonObject;

final class McpClientState {

    static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    final AtomicInteger requestIdGenerator;
    final List<JsonObject> requests;
    final List<JsonObject> responses;
    final ConcurrentMap<Integer, JsonObject> responseMap;
    final List<JsonObject> notifications;

    McpClientState() {
        this.requestIdGenerator = new AtomicInteger();
        this.requests = new CopyOnWriteArrayList<>();
        this.responses = new CopyOnWriteArrayList<>();
        this.responseMap = new ConcurrentHashMap<>();
        this.notifications = new CopyOnWriteArrayList<>();
    }

    void addResponse(JsonObject response) {
        responses.add(response);
        Integer id = response.getInteger("id");
        if (id != null) {
            responseMap.put(id, response);
        }
    }

    public int nextRequestId() {
        return requestIdGenerator.incrementAndGet();
    }

    public JsonObject waitForLastResponse() {
        int lastId = requestIdGenerator.get();
        if (lastId == 0) {
            return null;
        }
        Awaitility.await().pollInterval(POLL_INTERVAL).until(() -> responseMap.containsKey(lastId));
        return responseMap.get(lastId);
    }

    public List<JsonObject> waitForNotifications(int count) {
        Awaitility.await().pollInterval(POLL_INTERVAL).until(() -> notifications.size() >= count);
        return notifications;
    }

    public List<JsonObject> waitForRequests(int count) {
        Awaitility.await().pollInterval(POLL_INTERVAL).until(() -> requests.size() >= count);
        return requests;
    }

    public List<JsonObject> waitForResponses(int count) {
        Awaitility.await().pollInterval(POLL_INTERVAL).until(() -> responses.size() >= count);
        return responses;
    }

    public JsonObject waitForResponse(JsonObject request) {
        int id = request.getInteger("id");
        Awaitility.await().pollInterval(POLL_INTERVAL).until(() -> responseMap.containsKey(id));
        return responseMap.get(id);
    }

    public JsonObject getResponse(int id) {
        return responseMap.get(id);
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
