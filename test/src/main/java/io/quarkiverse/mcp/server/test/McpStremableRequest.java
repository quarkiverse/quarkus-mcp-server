package io.quarkiverse.mcp.server.test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.quarkiverse.mcp.server.sse.client.SseClient.SseEvent;
import io.quarkiverse.mcp.server.sse.client.SseClient.SseEventSubscriber;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;

class McpStremableRequest {

    private final HttpClient httpClient;
    private final URI mcpEndpoint;
    private final MultiMap headers;

    private final Consumer<JsonObject> requests;
    private final Consumer<JsonObject> responses;
    private final Consumer<JsonObject> notifications;

    private final AtomicReference<HttpHeaders> responseHeaders;

    McpStremableRequest(HttpClient httpClient, URI mcpEndpoint, MultiMap headers, Consumer<JsonObject> responses,
            Consumer<JsonObject> notifications, Consumer<JsonObject> requests) {
        this.httpClient = httpClient;
        this.mcpEndpoint = mcpEndpoint;
        this.headers = headers;
        this.requests = requests;
        this.responses = responses;
        this.notifications = notifications;
        this.responseHeaders = new AtomicReference<>();
    }

    protected void acceptMessage(JsonObject message) {
        if (message.containsKey("id")) {
            if (message.containsKey("result") || message.containsKey("error")) {
                responses.accept(message);
            } else {
                requests.accept(message);
            }
        } else {
            notifications.accept(message);
        }
    }

    protected void process(SseEvent event) {
        if ("message".equals(event.name())) {
            JsonObject json = new JsonObject(event.data());
            acceptMessage(json);
        }
    }

    void send(String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(mcpEndpoint)
                .version(Version.HTTP_1_1)
                .header("Accept", "text/event-stream")
                .header("Accept", "application/json")
                .POST(BodyPublishers.ofString(body));
        headers.forEach(builder::header);
        HttpRequest request = builder.build();

        CompletableFuture<HttpResponse<Object>> cf = httpClient.sendAsync(request, responseInfo -> {
            responseHeaders.set(responseInfo.headers());
            String contentType = responseInfo.headers().firstValue("Content-Type").orElseThrow();
            if ("application/json".equals(contentType)) {
                return cast(BodySubscribers.ofString(StandardCharsets.UTF_8));
            } else if ("text/event-stream".equals(contentType)) {
                return cast(BodySubscribers.fromLineSubscriber(new SseEventSubscriber(this::process)));
            } else {
                throw new IllegalStateException("Unsupported content type: " + contentType);
            }
        });

        // For SSE the CF never completes but for JSON it does
        cf.whenComplete((r, t) -> {
            if (t == null) {
                JsonObject json = new JsonObject(r.body().toString());
                acceptMessage(json);
            }
        });
    }

    HttpHeaders responseHeaders() {
        return responseHeaders.get();
    }

    @SuppressWarnings("unchecked")
    static <T> T cast(Object obj) {
        return (T) obj;
    }

}
