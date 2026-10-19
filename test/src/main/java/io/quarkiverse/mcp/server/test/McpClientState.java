package io.quarkiverse.mcp.server.test;

import java.time.Duration;
import java.util.ArrayList;
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
    private final List<JsonObject> requests;
    private final List<JsonObject> responses;
    final ConcurrentMap<Integer, JsonObject> responseMap;
    private final List<JsonObject> notifications;
    private final List<JsonObject> receivedMessages;

    McpClientState() {
        this.requestIdGenerator = new AtomicInteger();
        this.requests = new CopyOnWriteArrayList<>();
        this.responses = new CopyOnWriteArrayList<>();
        this.responseMap = new ConcurrentHashMap<>();
        this.notifications = new CopyOnWriteArrayList<>();
        this.receivedMessages = new ArrayList<>();
    }

    synchronized void addRequest(JsonObject request) {
        receivedMessages.add(request);
        requests.add(request);
    }

    synchronized void addNotification(JsonObject notification) {
        receivedMessages.add(notification);
        notifications.add(notification);
    }

    synchronized void addResponse(JsonObject response) {
        receivedMessages.add(response);
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

    public synchronized List<JsonObject> getReceivedMessages() {
        return List.copyOf(receivedMessages);
    }

    public synchronized void clearRequests() {
        receivedMessages.removeAll(requests);
        requests.clear();
    }

    public synchronized Snapshot toSnapshot() {
        return new Snapshot(getRequests(), getResponses(), getNotifications(), getReceivedMessages());
    }

}
