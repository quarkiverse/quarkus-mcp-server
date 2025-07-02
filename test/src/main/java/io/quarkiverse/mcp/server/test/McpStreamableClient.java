package io.quarkiverse.mcp.server.test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import io.vertx.core.MultiMap;

class McpStreamableClient {

    final HttpClient httpClient;
    final URI mcpEndpoint;
    final McpClientState state;

    McpStreamableClient(URI mcpEndpoint) {
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

}
