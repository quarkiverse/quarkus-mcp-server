package io.quarkiverse.mcp.server.test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientWebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.json.JsonObject;

class McpWebSocketClient {

    final McpClientState state;
    final WebSocketClient client;
    final ClientWebSocket webSocket;
    final List<Buffer> messages = new CopyOnWriteArrayList<>();
    final AtomicReference<Consumer<JsonObject>> requestConsumer = new AtomicReference<>();

    public void setRequestConsumer(Consumer<JsonObject> value) {
        this.requestConsumer.set(value);
    }

    public McpWebSocketClient(URI endpointUri, Vertx vertx, MultiMap headers) {
        this.state = new McpClientState();
        WebSocketClientOptions clientOptions = new WebSocketClientOptions();
        this.client = vertx.createWebSocketClient(clientOptions);
        this.webSocket = client.webSocket();
        webSocket.handler(b -> {
            messages.add(b);
            JsonObject json = new JsonObject(b);
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
        });
        WebSocketConnectOptions connectOptions = new WebSocketConnectOptions();
        headers.forEach(connectOptions::addHeader);
        connectOptions.setPort(endpointUri.getPort());
        connectOptions.setHost(endpointUri.getHost());
        connectOptions.setURI(endpointUri.getPath());
        webSocket.connect(connectOptions).toCompletionStage()
                .toCompletableFuture().join();
    }

    Future<Void> send(String message) {
        return webSocket.writeTextMessage(message);
    }

    void disconnect() {
        messages.clear();
        webSocket.close().toCompletionStage().toCompletableFuture().join();
    }

}
