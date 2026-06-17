package io.quarkiverse.mcp.server.test.dummyinit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.Sampling;
import io.quarkiverse.mcp.server.SamplingMessage;
import io.quarkiverse.mcp.server.SamplingRequest;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.http.runtime.StreamableHttpMcpMessageHandler;
import io.quarkiverse.mcp.server.sse.client.SseClient.SseEventSubscriber;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

public class AutoInitMetaTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.http.streamable.auto-init", "true");

    @Test
    public void testAutoInitWithMeta() throws Exception {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        URI endpoint = client.mcpEndpoint();
        client.terminateSession();
        client.disconnect();

        // Send a tools/call with _meta containing clientInfo and clientCapabilities
        // The Sampling param forces SSE mode; the tool sends a sampling request
        CompletableFuture<JsonObject> toolResponse = new CompletableFuture<>();
        HttpClient httpClient = HttpClient.newHttpClient();

        httpClient.sendAsync(
                autoInitRequest(endpoint, toolsCallWithMeta("info", 1), null),
                BodyHandlers.fromLineSubscriber(
                        new SseEventSubscriber(event -> {
                            if ("message".equals(event.name())) {
                                JsonObject json = new JsonObject(event.data());
                                if (json.containsKey("method")) {
                                    // Server request - respond to sampling/createMessage
                                    assertEquals("sampling/createMessage", json.getString("method"));
                                    JsonObject samplingResponse = new JsonObject()
                                            .put("jsonrpc", "2.0")
                                            .put("id", json.getValue("id"))
                                            .put("result", new JsonObject()
                                                    .put("role", "assistant")
                                                    .put("model", "test-model")
                                                    .put("content", new JsonObject()
                                                            .put("type", "text")
                                                            .put("text", "Sampling works!")));
                                    // Send the sampling response back
                                    httpClient.sendAsync(
                                            autoInitRequest(endpoint, samplingResponse, null),
                                            BodyHandlers.discarding());
                                } else if (json.containsKey("result")) {
                                    toolResponse.complete(json);
                                }
                            }
                        })));

        JsonObject response = toolResponse.get(5, TimeUnit.SECONDS);
        String text = response.getJsonObject("result").getJsonArray("content").getJsonObject(0).getString("text");
        assertEquals("TestClient:2.0:2025-03-26:Sampling works!", text);
    }

    @Test
    public void testAutoInitWithoutMeta() throws Exception {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        URI endpoint = client.mcpEndpoint();
        client.terminateSession();
        client.disconnect();

        // Send a tools/call without _meta; sampling is not supported
        // With lazy SSE init (default), the response is JSON because no SSE-dependent API is used
        HttpClient httpClient = HttpClient.newHttpClient();

        var httpResponse = httpClient.send(
                autoInitRequest(endpoint, toolsCallWithoutMeta("info", 2), null),
                BodyHandlers.ofString());

        assertEquals(200, httpResponse.statusCode());
        JsonObject response = new JsonObject(httpResponse.body());
        String text = response.getJsonObject("result").getJsonArray("content").getJsonObject(0).getString("text");
        assertEquals(StreamableHttpMcpMessageHandler.AUTO_INIT_IMPL_NAME + ":1:2025-03-26:not supported", text);
    }

    @Test
    public void testAutoInitWithProtocolVersionHeader() throws Exception {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        URI endpoint = client.mcpEndpoint();
        client.terminateSession();
        client.disconnect();

        // Send a tools/call with Mcp-Protocol-Version header and _meta
        CompletableFuture<JsonObject> toolResponse = new CompletableFuture<>();
        HttpClient httpClient = HttpClient.newHttpClient();

        httpClient.sendAsync(
                autoInitRequest(endpoint, toolsCallWithMeta("info", 3), "2025-11-25"),
                BodyHandlers.fromLineSubscriber(
                        new SseEventSubscriber(event -> {
                            if ("message".equals(event.name())) {
                                JsonObject json = new JsonObject(event.data());
                                if (json.containsKey("method")) {
                                    assertEquals("sampling/createMessage", json.getString("method"));
                                    JsonObject samplingResponse = new JsonObject()
                                            .put("jsonrpc", "2.0")
                                            .put("id", json.getValue("id"))
                                            .put("result", new JsonObject()
                                                    .put("role", "assistant")
                                                    .put("model", "test-model")
                                                    .put("content", new JsonObject()
                                                            .put("type", "text")
                                                            .put("text", "Sampling works!")));
                                    httpClient.sendAsync(
                                            autoInitRequest(endpoint, samplingResponse, "2025-11-25"),
                                            BodyHandlers.discarding());
                                } else if (json.containsKey("result")) {
                                    toolResponse.complete(json);
                                }
                            }
                        })));

        JsonObject response = toolResponse.get(5, TimeUnit.SECONDS);
        String text = response.getJsonObject("result").getJsonArray("content").getJsonObject(0).getString("text");
        assertEquals("TestClient:2.0:2025-11-25:Sampling works!", text);
    }

    private static HttpRequest autoInitRequest(URI endpoint, JsonObject body, String protocolVersion) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(endpoint)
                .version(Version.HTTP_1_1)
                .header("Accept", "text/event-stream")
                .header("Accept", "application/json")
                .POST(BodyPublishers.ofString(body.encode()));
        if (protocolVersion != null) {
            builder.header(StreamableHttpMcpMessageHandler.MCP_PROTOCOL_VERSION_HEADER, protocolVersion);
        }
        return builder.build();
    }

    private JsonObject toolsCallWithMeta(String name, int id) {
        return new JsonObject()
                .put("jsonrpc", "2.0")
                .put("method", McpAssured.TOOLS_CALL)
                .put("id", id)
                .put("params", new JsonObject()
                        .put("name", name)
                        .put("_meta", new JsonObject()
                                .put(MetaKey.CLIENT_INFO.toString(),
                                        new JsonObject().put("name", "TestClient").put("version", "2.0"))
                                .put(MetaKey.CLIENT_CAPABILITIES.toString(),
                                        new JsonObject().put("sampling", new JsonObject()))));
    }

    private JsonObject toolsCallWithoutMeta(String name, int id) {
        return new JsonObject()
                .put("jsonrpc", "2.0")
                .put("method", McpAssured.TOOLS_CALL)
                .put("id", id)
                .put("params", new JsonObject().put("name", name));
    }

    public static class MyTools {

        @Tool
        Uni<String> info(McpConnection connection, Sampling sampling) {
            String prefix = connection.initialRequest().implementation().name()
                    + ":" + connection.initialRequest().implementation().version()
                    + ":" + connection.initialRequest().protocolVersion().version()
                    + ":";
            if (sampling.isSupported()) {
                SamplingRequest request = sampling.requestBuilder()
                        .setMaxTokens(100)
                        .addMessage(SamplingMessage.withUserRole("Test"))
                        .build();
                return request.send().map(sr -> prefix + sr.content().asText().text());
            }
            return Uni.createFrom().item(prefix + "not supported");
        }
    }

}
