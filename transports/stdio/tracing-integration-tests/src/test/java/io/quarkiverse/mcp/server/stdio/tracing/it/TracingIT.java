package io.quarkiverse.mcp.server.stdio.tracing.it;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStdioTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.PromptInfo;
import io.quarkiverse.mcp.server.test.McpAssured.ResourceInfo;
import io.quarkiverse.mcp.server.test.McpAssured.ToolInfo;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class TracingIT {

    // No need to reset because a server is spawned by each test method.

    @Test
    void testPrompt() {
        try (McpStdioTestClient client = McpAssured.newStdioClient().setProtocolVersion(McpProtocolVersion.V_2026_07_28)
                .build().connect()) {
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

            final JsonArray spans = awaitSpans(3);

            // Server discovery
            final JsonObject discover = findSpan(spans, "server/discover");
            assertNotNull(discover);
            assertEquals("0000000000000000", discover.getString("parent_spanId"));
            assertEquals("SERVER", discover.getString("kind"));
            assertEquals("server/discover", discover.getJsonObject("attributes").getString("mcp.method.name"));
            assertNull(discover.getJsonObject("attributes").getString("mcp.session.id"));
            assertEquals("2026-07-28", discover.getJsonObject("attributes").getString("mcp.protocol.version"));
            assertNull(discover.getJsonObject("attributes").getString("network.protocol.name"));
            assertEquals("pipe", discover.getJsonObject("attributes").getString("network.transport"));

            // Verify prompts/list span
            JsonObject listSpan = findSpan(spans, "prompts/list");
            assertNotNull(listSpan);
            assertEquals("0000000000000000", listSpan.getString("parent_spanId"));
            assertEquals("SERVER", listSpan.getString("kind"));
            assertNull(listSpan.getJsonObject("attributes").getString("mcp.session.id"));
            assertEquals("prompts/list", listSpan.getJsonObject("attributes").getString("mcp.method.name"));
            assertNotNull(listSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
            assertEquals("2026-07-28", listSpan.getJsonObject("attributes").getString("mcp.protocol.version"));
            assertEquals("pipe", listSpan.getJsonObject("attributes").getString("network.transport"));
            assertNull(listSpan.getJsonObject("attributes").getString("network.protocol.name"));
            assertNull(listSpan.getJsonObject("attributes").getString("network.protocol.version"));

            // Verify prompts/get span
            JsonObject getSpan = findSpan(spans, "prompts/get code_assist");
            assertNotNull(getSpan);
            assertEquals("0000000000000000", getSpan.getString("parent_spanId"));
            assertEquals("SERVER", getSpan.getString("kind"));
            assertNull(getSpan.getJsonObject("attributes").getString("mcp.session.id"));
            assertEquals("prompts/get", getSpan.getJsonObject("attributes").getString("mcp.method.name"));
            assertEquals("code_assist", getSpan.getJsonObject("attributes").getString("gen_ai.prompt.name"));
            assertNotNull(getSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
            assertEquals("2026-07-28", getSpan.getJsonObject("attributes").getString("mcp.protocol.version"));
            assertEquals("pipe", getSpan.getJsonObject("attributes").getString("network.transport"));
            assertNull(getSpan.getJsonObject("attributes").getString("network.protocol.name"));
            assertNull(getSpan.getJsonObject("attributes").getString("network.protocol.version"));
        }
    }

    @Test
    void testTool() {
        try (McpStdioTestClient client = McpAssured.newStdioClient().setProtocolVersion(McpProtocolVersion.V_2026_07_28)
                .build().connect()) {
            client.when()
                    .toolsList(page -> {
                        assertEquals(1, page.size());
                        ToolInfo tool = page.findByName("toLowerCase");
                        assertNotNull(tool);
                        assertNotNull(tool.inputSchema());
                        assertEquals(1, tool.inputSchema().getJsonObject("properties").size());
                    })
                    .toolsCall("toLowerCase", Map.of("value", "LooP"), response -> {
                        assertEquals("loop", response.firstContent().asText().text());
                    })
                    .thenAssertResults();

            final JsonArray spans = awaitSpans(3);

            // Server discovery
            final JsonObject discover = findSpan(spans, "server/discover");
            assertNotNull(discover);
            assertEquals("0000000000000000", discover.getString("parent_spanId"));
            assertEquals("SERVER", discover.getString("kind"));
            assertEquals("server/discover", discover.getJsonObject("attributes").getString("mcp.method.name"));
            assertNull(discover.getJsonObject("attributes").getString("mcp.session.id"));
            assertEquals("2026-07-28", discover.getJsonObject("attributes").getString("mcp.protocol.version"));
            assertNull(discover.getJsonObject("attributes").getString("network.protocol.name"));
            assertEquals("pipe", discover.getJsonObject("attributes").getString("network.transport"));

            // Verify tools/list span
            JsonObject listSpan = findSpan(spans, "tools/list");
            assertNotNull(listSpan);
            assertEquals("0000000000000000", listSpan.getString("parent_spanId"));
            assertEquals("SERVER", listSpan.getString("kind"));
            assertNull(listSpan.getJsonObject("attributes").getString("mcp.session.id"));
            assertEquals("tools/list", listSpan.getJsonObject("attributes").getString("mcp.method.name"));
            assertNotNull(listSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
            assertEquals("2026-07-28", listSpan.getJsonObject("attributes").getString("mcp.protocol.version"));
            assertEquals("pipe", listSpan.getJsonObject("attributes").getString("network.transport"));
            assertNull(listSpan.getJsonObject("attributes").getString("network.protocol.name"));
            assertNull(listSpan.getJsonObject("attributes").getString("network.protocol.version"));

            // Verify tools/call span
            JsonObject callSpan = findSpan(spans, "tools/call toLowerCase");
            assertNotNull(callSpan);
            assertEquals("0000000000000000", callSpan.getString("parent_spanId"));
            assertEquals("SERVER", callSpan.getString("kind"));
            assertNull(callSpan.getJsonObject("attributes").getString("mcp.session.id"));
            assertEquals("tools/call", callSpan.getJsonObject("attributes").getString("mcp.method.name"));
            assertEquals("toLowerCase", callSpan.getJsonObject("attributes").getString("gen_ai.tool.name"));
            assertEquals("execute_tool", callSpan.getJsonObject("attributes").getString("gen_ai.operation.name"));
            assertNotNull(callSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
            assertEquals("2026-07-28", callSpan.getJsonObject("attributes").getString("mcp.protocol.version"));
            assertEquals("pipe", callSpan.getJsonObject("attributes").getString("network.transport"));
            assertNull(callSpan.getJsonObject("attributes").getString("network.protocol.name"));
            assertNull(callSpan.getJsonObject("attributes").getString("network.protocol.version"));
            assertEquals("UNSET", callSpan.getString("statusCode"));
        }
    }

    @Test
    void testResource() {
        try (McpStdioTestClient client = McpAssured.newStdioClient().setProtocolVersion(McpProtocolVersion.V_2026_07_28)
                .build().connect()) {
            client.when()
                    .resourcesList(page -> {
                        assertEquals(1, page.size());
                        ResourceInfo resource = page.findByUri("file:///project/alpha");
                        assertNotNull(resource);
                        assertEquals("alpha", resource.name());
                    })
                    .resourcesRead("file:///project/alpha", response -> {
                        assertEquals(1, response.contents().size());
                        BlobResourceContents blob = response.contents().get(0).asBlob();
                        assertEquals("file:///project/alpha", blob.uri());
                        assertEquals(Base64.getMimeEncoder().encodeToString("data".getBytes()),
                                blob.blob());
                    })
                    .thenAssertResults();

            final JsonArray spans = awaitSpans(3);

            // Server discovery
            final JsonObject discover = findSpan(spans, "server/discover");
            assertNotNull(discover);
            assertEquals("0000000000000000", discover.getString("parent_spanId"));
            assertEquals("SERVER", discover.getString("kind"));
            assertEquals("server/discover", discover.getJsonObject("attributes").getString("mcp.method.name"));
            assertNull(discover.getJsonObject("attributes").getString("mcp.session.id"));
            assertEquals("2026-07-28", discover.getJsonObject("attributes").getString("mcp.protocol.version"));
            assertNull(discover.getJsonObject("attributes").getString("network.protocol.name"));
            assertEquals("pipe", discover.getJsonObject("attributes").getString("network.transport"));

            // Verify resources/list span
            JsonObject listSpan = findSpan(spans, "resources/list");
            assertNotNull(listSpan);
            assertEquals("0000000000000000", listSpan.getString("parent_spanId"));
            assertEquals("SERVER", listSpan.getString("kind"));
            assertNull(listSpan.getJsonObject("attributes").getString("mcp.session.id"));
            assertEquals("resources/list", listSpan.getJsonObject("attributes").getString("mcp.method.name"));
            assertNotNull(listSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
            assertEquals("2026-07-28", listSpan.getJsonObject("attributes").getString("mcp.protocol.version"));
            assertEquals("pipe", listSpan.getJsonObject("attributes").getString("network.transport"));
            assertNull(listSpan.getJsonObject("attributes").getString("network.protocol.name"));
            assertNull(listSpan.getJsonObject("attributes").getString("network.protocol.version"));

            // Verify resources/read span
            JsonObject readSpan = findSpan(spans, "resources/read");
            assertNotNull(readSpan);
            assertEquals("0000000000000000", readSpan.getString("parent_spanId"));
            assertEquals("SERVER", readSpan.getString("kind"));
            assertNull(readSpan.getJsonObject("attributes").getString("mcp.session.id"));
            assertEquals("resources/read", readSpan.getJsonObject("attributes").getString("mcp.method.name"));
            assertEquals("file:///project/alpha", readSpan.getJsonObject("attributes").getString("mcp.resource.uri"));
            assertNotNull(readSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
            assertEquals("2026-07-28", readSpan.getJsonObject("attributes").getString("mcp.protocol.version"));
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
        JsonArray spans = holder.get();
        for (int i = 0; i < spans.size(); i++) {
            String name = spans.getJsonObject(i).getString("name");
            assertFalse("initialize".equals(name) || "notifications/initialized".equals(name));
        }
        return spans;
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
