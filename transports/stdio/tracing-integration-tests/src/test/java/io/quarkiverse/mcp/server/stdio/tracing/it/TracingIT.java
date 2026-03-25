package io.quarkiverse.mcp.server.stdio.tracing.it;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStdioTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.PromptInfo;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class TracingIT {

    // No need to reset because a server is spawned by each test method.

    @Test
    void testPrompt() {
        try (McpStdioTestClient client = McpAssured.newConnectedStdioClient()) {
            client.when()
                    .promptsList(page -> {
                        assertEquals(1, page.size());
                        PromptInfo prompt = page.findByName("code_assist");
                        assertNotNull(prompt);
                        assertEquals(1, prompt.arguments().size());
                        assertEquals("lang", prompt.arguments().get(0).name());
                        assertTrue(prompt.arguments().get(0).required());
                    })
                    .promptsGet("code_assist", Map.of("lang", "java"), response -> {
                        assertEquals("System.out.println(\"Hello world!\");",
                                response.messages().get(0).content().asText().text());
                    })
                    .thenAssertResults();

            final JsonArray spans = awaitSpans(4);

            // Initialize
            final JsonObject initialize = findSpan(spans, "initialize");
            assertNotNull(initialize);
            assertEquals("0000000000000000", initialize.getString("parent_spanId"));
            assertNotNull(initialize.getJsonObject("attributes").getString("mcp.session.id"));
            final String mcpSessionId = initialize.getJsonObject("attributes").getString("mcp.session.id");
            assertNull(initialize.getJsonObject("attributes").getString("network.protocol.name"));
            assertEquals("pipe", initialize.getJsonObject("attributes").getString("network.transport"));

            // All spans have their own trace but have the same mcp.session.id.

            // Notification initialized
            final JsonObject notifInitialized = findSpan(spans, "notifications/initialized");
            assertNotNull(notifInitialized);
            assertEquals("0000000000000000", notifInitialized.getString("parent_spanId"));
            assertEquals(mcpSessionId, notifInitialized.getJsonObject("attributes").getString("mcp.session.id"));
            assertNull(notifInitialized.getJsonObject("attributes").getString("network.protocol.name"));
            assertEquals("pipe", notifInitialized.getJsonObject("attributes").getString("network.transport"));

            // Verify prompts/list span
            JsonObject listSpan = findSpan(spans, "prompts/list");
            assertNotNull(listSpan);
            assertEquals("0000000000000000", listSpan.getString("parent_spanId"));
            assertEquals("SERVER", listSpan.getString("kind"));
            assertEquals(mcpSessionId, listSpan.getJsonObject("attributes").getString("mcp.session.id"));
            assertEquals("prompts/list", listSpan.getJsonObject("attributes").getString("mcp.method.name"));
            assertNotNull(listSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
            assertNotNull(listSpan.getJsonObject("attributes").getString("mcp.protocol.version"));
            assertEquals("pipe", listSpan.getJsonObject("attributes").getString("network.transport"));
            assertNull(listSpan.getJsonObject("attributes").getString("network.protocol.name"));
            assertNull(listSpan.getJsonObject("attributes").getString("network.protocol.version"));

            // Verify prompts/get span
            JsonObject getSpan = findSpan(spans, "prompts/get code_assist");
            assertNotNull(getSpan);
            assertEquals("0000000000000000", getSpan.getString("parent_spanId"));
            assertEquals("SERVER", getSpan.getString("kind"));
            assertEquals(mcpSessionId, getSpan.getJsonObject("attributes").getString("mcp.session.id"));
            assertEquals("prompts/get", getSpan.getJsonObject("attributes").getString("mcp.method.name"));
            assertEquals("code_assist", getSpan.getJsonObject("attributes").getString("gen_ai.prompt.name"));
            assertNotNull(getSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
            assertNotNull(getSpan.getJsonObject("attributes").getString("mcp.protocol.version"));
            assertEquals("pipe", getSpan.getJsonObject("attributes").getString("network.transport"));
            assertNull(getSpan.getJsonObject("attributes").getString("network.protocol.name"));
            assertNull(getSpan.getJsonObject("attributes").getString("network.protocol.version"));
        }
    }

    @Test
    void testTool() {
        try (McpStdioTestClient client = McpAssured.newConnectedStdioClient()) {
            client.when()
                    .toolsList(page -> {
                        assertEquals(1, page.size());
                        var tool = page.findByName("toLowerCase");
                        assertNotNull(tool);
                        assertNotNull(tool.inputSchema());
                        assertEquals(1, tool.inputSchema().getJsonObject("properties").size());
                    })
                    .toolsCall("toLowerCase", Map.of("value", "LooP"), response -> {
                        assertEquals("loop", response.firstContent().asText().text());
                    })
                    .thenAssertResults();

            final JsonArray spans = awaitSpans(4);

            // Initialize
            final JsonObject initialize = findSpan(spans, "initialize");
            assertNotNull(initialize);
            assertEquals("0000000000000000", initialize.getString("parent_spanId"));
            assertNotNull(initialize.getJsonObject("attributes").getString("mcp.session.id"));
            final String mcpSessionId = initialize.getJsonObject("attributes").getString("mcp.session.id");
            assertNull(initialize.getJsonObject("attributes").getString("network.protocol.name"));
            assertEquals("pipe", initialize.getJsonObject("attributes").getString("network.transport"));

            // All spans have their own trace but have the same mcp.session.id.

            // Notification initialized
            final JsonObject notifInitialized = findSpan(spans, "notifications/initialized");
            assertNotNull(notifInitialized);
            assertEquals("0000000000000000", notifInitialized.getString("parent_spanId"));
            assertEquals(mcpSessionId, notifInitialized.getJsonObject("attributes").getString("mcp.session.id"));
            assertNull(notifInitialized.getJsonObject("attributes").getString("network.protocol.name"));
            assertEquals("pipe", notifInitialized.getJsonObject("attributes").getString("network.transport"));

            // Verify tools/list span
            JsonObject listSpan = findSpan(spans, "tools/list");
            assertNotNull(listSpan);
            assertEquals("0000000000000000", listSpan.getString("parent_spanId"));
            assertEquals("SERVER", listSpan.getString("kind"));
            assertEquals(mcpSessionId, listSpan.getJsonObject("attributes").getString("mcp.session.id"));
            assertEquals("tools/list", listSpan.getJsonObject("attributes").getString("mcp.method.name"));
            assertNotNull(listSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
            assertNotNull(listSpan.getJsonObject("attributes").getString("mcp.protocol.version"));
            assertEquals("pipe", listSpan.getJsonObject("attributes").getString("network.transport"));
            assertNull(listSpan.getJsonObject("attributes").getString("network.protocol.name"));
            assertNull(listSpan.getJsonObject("attributes").getString("network.protocol.version"));

            // Verify tools/call span
            JsonObject callSpan = findSpan(spans, "tools/call toLowerCase");
            assertNotNull(callSpan);
            assertEquals("0000000000000000", callSpan.getString("parent_spanId"));
            assertEquals("SERVER", callSpan.getString("kind"));
            assertEquals(mcpSessionId, callSpan.getJsonObject("attributes").getString("mcp.session.id"));
            assertEquals("tools/call", callSpan.getJsonObject("attributes").getString("mcp.method.name"));
            assertEquals("toLowerCase", callSpan.getJsonObject("attributes").getString("gen_ai.tool.name"));
            assertEquals("execute_tool", callSpan.getJsonObject("attributes").getString("gen_ai.operation.name"));
            assertNotNull(callSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
            assertNotNull(callSpan.getJsonObject("attributes").getString("mcp.protocol.version"));
            assertEquals("pipe", callSpan.getJsonObject("attributes").getString("network.transport"));
            assertNull(callSpan.getJsonObject("attributes").getString("network.protocol.name"));
            assertNull(callSpan.getJsonObject("attributes").getString("network.protocol.version"));
            assertEquals("UNSET", callSpan.getString("statusCode"));
        }
    }

    @Test
    void testResource() {
        try (McpStdioTestClient client = McpAssured.newConnectedStdioClient()) {
            client.when()
                    .resourcesList(page -> {
                        assertEquals(1, page.size());
                        var resource = page.findByUri("file:///project/alpha");
                        assertNotNull(resource);
                        assertEquals("alpha", resource.name());
                    })
                    .resourcesRead("file:///project/alpha", response -> {
                        assertEquals(1, response.contents().size());
                        var blob = response.contents().get(0).asBlob();
                        assertEquals("file:///project/alpha", blob.uri());
                        assertEquals(Base64.getMimeEncoder().encodeToString("data".getBytes()),
                                blob.blob());
                    })
                    .thenAssertResults();

            final JsonArray spans = awaitSpans(4);

            // Initialize
            final JsonObject initialize = findSpan(spans, "initialize");
            assertNotNull(initialize);
            assertEquals("0000000000000000", initialize.getString("parent_spanId"));
            assertNotNull(initialize.getJsonObject("attributes").getString("mcp.session.id"));
            final String mcpSessionId = initialize.getJsonObject("attributes").getString("mcp.session.id");
            assertNull(initialize.getJsonObject("attributes").getString("network.protocol.name"));
            assertEquals("pipe", initialize.getJsonObject("attributes").getString("network.transport"));

            // All spans have their own trace but have the same mcp.session.id.

            // Notification initialized
            final JsonObject notifInitialized = findSpan(spans, "notifications/initialized");
            assertNotNull(notifInitialized);
            assertEquals("0000000000000000", notifInitialized.getString("parent_spanId"));
            assertEquals(mcpSessionId, notifInitialized.getJsonObject("attributes").getString("mcp.session.id"));
            assertNull(notifInitialized.getJsonObject("attributes").getString("network.protocol.name"));
            assertEquals("pipe", notifInitialized.getJsonObject("attributes").getString("network.transport"));

            // Verify resources/list span
            JsonObject listSpan = findSpan(spans, "resources/list");
            assertNotNull(listSpan);
            assertEquals("0000000000000000", listSpan.getString("parent_spanId"));
            assertEquals("SERVER", listSpan.getString("kind"));
            assertEquals(mcpSessionId, listSpan.getJsonObject("attributes").getString("mcp.session.id"));
            assertEquals("resources/list", listSpan.getJsonObject("attributes").getString("mcp.method.name"));
            assertNotNull(listSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
            assertNotNull(listSpan.getJsonObject("attributes").getString("mcp.protocol.version"));
            assertEquals("pipe", listSpan.getJsonObject("attributes").getString("network.transport"));
            assertNull(listSpan.getJsonObject("attributes").getString("network.protocol.name"));
            assertNull(listSpan.getJsonObject("attributes").getString("network.protocol.version"));

            // Verify resources/read span
            JsonObject readSpan = findSpan(spans, "resources/read");
            assertNotNull(readSpan);
            assertEquals("0000000000000000", readSpan.getString("parent_spanId"));
            assertEquals("SERVER", readSpan.getString("kind"));
            assertEquals(mcpSessionId, readSpan.getJsonObject("attributes").getString("mcp.session.id"));
            assertEquals("resources/read", readSpan.getJsonObject("attributes").getString("mcp.method.name"));
            assertEquals("file:///project/alpha", readSpan.getJsonObject("attributes").getString("mcp.resource.uri"));
            assertNotNull(readSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
            assertNotNull(readSpan.getJsonObject("attributes").getString("mcp.protocol.version"));
            assertEquals("pipe", readSpan.getJsonObject("attributes").getString("network.transport"));
            assertNull(readSpan.getJsonObject("attributes").getString("network.protocol.name"));
            assertNull(readSpan.getJsonObject("attributes").getString("network.protocol.version"));
        }
    }

    private JsonArray awaitSpans(int expectedCount) {
        HttpClient httpClient = HttpClient.newHttpClient();
        AtomicReference<JsonArray> holder = new AtomicReference<>();
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/spans"))
                            .GET()
                            .build();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() != 200) {
                        return false;
                    }
                    JsonArray spans = new JsonArray(response.body());
                    if (spans.size() >= expectedCount) {
                        holder.set(spans);
                        return true;
                    }
                    return false;
                });
        return holder.get();
    }

    private JsonObject findSpan(JsonArray spans, String spanName) {
        for (int i = 0; i < spans.size(); i++) {
            JsonObject s = spans.getJsonObject(i);
            if (spanName.equals(s.getString("name"))) {
                return s;
            }
        }
        throw new AssertionError("Span not found: " + spanName);
    }
}
