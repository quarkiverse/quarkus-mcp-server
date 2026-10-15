package io.quarkiverse.mcp.server.test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.quarkiverse.mcp.server.sse.client.SseClient;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;

class McpStreamableClient extends SseClient {

    final URI mcpEndpoint;
    final McpClientState state;
    private final Set<CompletableFuture<?>> pendingRequests = new HashSet<>();

    final AtomicReference<Consumer<JsonObject>> requestConsumer = new AtomicReference<>();

    McpStreamableClient(URI mcpEndpoint) {
        super(mcpEndpoint);
        this.mcpEndpoint = mcpEndpoint;
        this.state = new McpClientState();
    }

    public void setRequestConsumer(Consumer<JsonObject> value) {
        this.requestConsumer.set(value);
    }

    // impl. note: the response is not reflected in the client state object
    HttpResponse<String> sendSync(String body, MultiMap headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(mcpEndpoint)
                .version(Version.HTTP_1_1)
                .header("Accept", "text/event-stream")
                .header("Accept", "application/json")
                .POST(BodyPublishers.ofString(body));
        headers.forEach(builder::header);
        return doSend(builder.build());
    }

    HttpResponse<String> sendTerminate(MultiMap headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(mcpEndpoint)
                .version(Version.HTTP_1_1)
                .header("Accept", "text/event-stream")
                .header("Accept", "application/json")
                .DELETE();
        headers.forEach(builder::header);
        return doSend(builder.build());
    }

    private HttpResponse<String> doSend(HttpRequest request) {
        try {
            return HttpClients.getDefault().send(request, BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    void send(String body, MultiMap headers) {
        McpStremableRequest request = new McpStremableRequest(HttpClients.getDefault(), mcpEndpoint, headers,
                state::addResponse,
                state.notifications::add, state.requests::add);
        track(request.send(body));
    }

    CompletableFuture<HttpResponse<Void>> connectSubsidiarySse(MultiMap headers) {
        // Track the original HTTP future so cancellation also closes the response stream.
        CompletableFuture<HttpResponse<Void>> response = HttpClients.getDefault().sendAsync(buildConnectRequest(headers),
                BodyHandlers.fromLineSubscriber(new SseEventSubscriber(this::process)));
        track(response);
        return response;
    }

    private synchronized void track(CompletableFuture<?> response) {
        pendingRequests.add(response);
        response.whenComplete((r, t) -> {
            synchronized (this) {
                pendingRequests.remove(response);
            }
        });
    }

    synchronized void disconnect() {
        // Cancellation callbacks remove completed requests from the set.
        for (CompletableFuture<?> response : Set.copyOf(pendingRequests)) {
            response.cancel(true);
        }
        pendingRequests.clear();
    }

    @Override
    protected void process(SseEvent event) {
        if ("message".equals(event.name())) {
            JsonObject json = new JsonObject(event.data());
            if (json.getValue("id") != null) {
                if (json.containsKey("result") || json.containsKey("error")) {
                    state.addResponse(json);
                } else {
                    state.requests.add(json);
                }
            } else {
                state.notifications.add(json);
            }
        }
    }

}
