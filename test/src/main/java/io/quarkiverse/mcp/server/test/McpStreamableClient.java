package io.quarkiverse.mcp.server.test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.CompletableFuture;

import io.quarkiverse.mcp.server.sse.client.SseClient;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;

class McpStreamableClient extends SseClient {

    final HttpClient httpClient;
    final URI mcpEndpoint;
    final McpClientState state;

    McpStreamableClient(URI mcpEndpoint) {
        super(mcpEndpoint);
        this.httpClient = HttpClient.newHttpClient();
        this.mcpEndpoint = mcpEndpoint;
        this.state = new McpClientState();
    }

    HttpResponse<String> sendSync(String body, MultiMap headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(mcpEndpoint)
                .version(Version.HTTP_1_1)
                .header("Accept", "text/event-stream")
                .header("Accept", "application/json")
                .POST(BodyPublishers.ofString(body));
        headers.forEach(builder::header);
        try {
            return httpClient.send(builder.build(), BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    void send(String body, MultiMap headers) {
        McpStremableRequest request = new McpStremableRequest(httpClient, mcpEndpoint, headers, state.responses::add,
                state.notifications::add, state.requests::add);
        request.send(body);
    }

    CompletableFuture<HttpResponse<Void>> connectSubsidiarySse(MultiMap headers) {
        return connect(httpClient, headers);
    }

    @Override
    protected void process(SseEvent event) {
        if ("message".equals(event.name())) {
            JsonObject json = new JsonObject(event.data());
            if (json.containsKey("id")) {
                if (json.containsKey("result") || json.containsKey("error")) {
                    state.responses.add(json);
                } else {
                    state.requests.add(json);
                }
            } else {
                state.notifications.add(json);
            }
        }
    }

}
