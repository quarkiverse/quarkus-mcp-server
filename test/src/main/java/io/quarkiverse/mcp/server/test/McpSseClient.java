package io.quarkiverse.mcp.server.test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.awaitility.Awaitility;

import io.quarkiverse.mcp.server.sse.client.SseClient;
import io.vertx.core.json.JsonObject;

public class McpSseClient extends SseClient {

    public final McpClientState state;

    private final List<SseEvent> allEvents;

    private final AtomicReference<Consumer<JsonObject>> requestConsumer = new AtomicReference<>();

    public McpSseClient(URI uri) {
        super(uri);
        this.allEvents = new CopyOnWriteArrayList<>();
        this.state = new McpClientState();
    }

    public void setRequestConsumer(Consumer<JsonObject> value) {
        this.requestConsumer.set(value);
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
                    state.responses.add(json);
                } else {
                    // Request from the server
                    state.requests.add(json);
                    Consumer<JsonObject> c = requestConsumer.get();
                    if (c != null) {
                        c.accept(json);
                    }
                }
            } else {
                state.notifications.add(json);
            }
        }
    }

}
