package io.quarkiverse.mcp.server.tracing.it;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.OpenTelemetry;
import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.ElicitationResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Tests OTel context propagation from client to server.
 * Unlike {@link TracingClientWithoutOtelTest} which verifies server-side spans without client tracing,
 * this test enables client-side tracing via {@code setTracing(openTelemetry)} and verifies
 * that server spans are parented to client spans via {@code _meta} context propagation.
 */
@QuarkusTest
@TestProfile(TracingClientWithoutOtelTest.OTelEnabledProfile.class)
class TracingContextPropagationTest {

    @Inject
    OpenTelemetry openTelemetry;

    @BeforeEach
    void resetSpans() {
        given().delete("/spans").then().statusCode(204);
    }

    @Test
    void testTool() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setTracing(openTelemetry)
                .build()
                .connect();
        client.when()
                .toolsList(p -> {
                    assertNotNull(p.findByName("toLowerCase"));
                })
                .toolsCall("toLowerCase", Map.of("value", "LooP"), r -> {
                    assertEquals("loop", r.firstContent().asText().text());
                })
                .thenAssertResults();

        final JsonArray spans = awaitSpans(8);

        // Initialize
        assertNotNull(findSpan(spans, "initialize", "CLIENT"));
        assertParentedBy(spans, "initialize");
        JsonObject initServer = findSpan(spans, "initialize", "SERVER");
        final String mcpSessionId = initServer.getJsonObject("attributes").getString("mcp.session.id");
        assertNotNull(mcpSessionId);
        assertEquals(client.mcpSessionId(), mcpSessionId);

        // Notification initialized
        assertNotNull(findSpan(spans, "notifications/initialized", "CLIENT"));
        assertParentedBy(spans, "notifications/initialized");
        assertEquals(mcpSessionId,
                findSpan(spans, "notifications/initialized", "SERVER").getJsonObject("attributes")
                        .getString("mcp.session.id"));

        // tools/list
        assertNotNull(findSpan(spans, "tools/list", "CLIENT"));
        assertParentedBy(spans, "tools/list");
        JsonObject listServer = findSpan(spans, "tools/list", "SERVER");
        assertEquals(mcpSessionId, listServer.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("tools/list", listServer.getJsonObject("attributes").getString("mcp.method.name"));

        // tools/call — SERVER span name includes tool name; CLIENT span is just "tools/call"
        assertNotNull(findSpan(spans, "tools/call", "CLIENT"));
        assertParentedBy(spans, "tools/call", "tools/call toLowerCase");
        JsonObject callServer = findSpan(spans, "tools/call toLowerCase");
        assertEquals(mcpSessionId, callServer.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("tools/call", callServer.getJsonObject("attributes").getString("mcp.method.name"));
        assertEquals("toLowerCase", callServer.getJsonObject("attributes").getString("gen_ai.tool.name"));
    }

    @Test
    void testPrompt() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setTracing(openTelemetry)
                .build()
                .connect();
        client.when()
                .promptsList(p -> {
                    assertEquals(1, p.size());
                })
                .promptsGet("code_assist", Map.of("lang", "java"), r -> {
                    assertEquals("System.out.println(\"Hello world!\");", r.messages().get(0).content().asText().text());
                })
                .thenAssertResults();

        final JsonArray spans = awaitSpans(8);

        // Initialize
        assertNotNull(findSpan(spans, "initialize", "CLIENT"));
        assertParentedBy(spans, "initialize");
        JsonObject initServer = findSpan(spans, "initialize", "SERVER");
        final String mcpSessionId = initServer.getJsonObject("attributes").getString("mcp.session.id");
        assertNotNull(mcpSessionId);
        assertEquals(client.mcpSessionId(), mcpSessionId);

        // Notification initialized
        assertNotNull(findSpan(spans, "notifications/initialized", "CLIENT"));
        assertParentedBy(spans, "notifications/initialized");
        assertEquals(mcpSessionId,
                findSpan(spans, "notifications/initialized", "SERVER").getJsonObject("attributes")
                        .getString("mcp.session.id"));

        // prompts/list
        assertNotNull(findSpan(spans, "prompts/list", "CLIENT"));
        assertParentedBy(spans, "prompts/list");
        JsonObject listServer = findSpan(spans, "prompts/list", "SERVER");
        assertEquals(mcpSessionId, listServer.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("prompts/list", listServer.getJsonObject("attributes").getString("mcp.method.name"));

        // prompts/get — SERVER span name includes prompt name; CLIENT span is just "prompts/get"
        assertNotNull(findSpan(spans, "prompts/get", "CLIENT"));
        assertParentedBy(spans, "prompts/get", "prompts/get code_assist");
        JsonObject getServer = findSpan(spans, "prompts/get code_assist");
        assertEquals(mcpSessionId, getServer.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("prompts/get", getServer.getJsonObject("attributes").getString("mcp.method.name"));
        assertEquals("code_assist", getServer.getJsonObject("attributes").getString("gen_ai.prompt.name"));
    }

    @Test
    void testResource() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setTracing(openTelemetry)
                .build()
                .connect();
        client.when()
                .resourcesList(p -> {
                    assertEquals(1, p.size());
                    assertEquals("alpha", p.findByUri("file:///project/alpha").name());
                })
                .resourcesRead("file:///project/alpha", r -> {
                    assertEquals(Base64.getMimeEncoder().encodeToString("data".getBytes()),
                            r.contents().get(0).asBlob().blob());
                })
                .thenAssertResults();

        final JsonArray spans = awaitSpans(8);

        // Initialize
        assertNotNull(findSpan(spans, "initialize", "CLIENT"));
        assertParentedBy(spans, "initialize");
        JsonObject initServer = findSpan(spans, "initialize", "SERVER");
        final String mcpSessionId = initServer.getJsonObject("attributes").getString("mcp.session.id");
        assertNotNull(mcpSessionId);
        assertEquals(client.mcpSessionId(), mcpSessionId);

        // Notification initialized
        assertNotNull(findSpan(spans, "notifications/initialized", "CLIENT"));
        assertParentedBy(spans, "notifications/initialized");
        assertEquals(mcpSessionId,
                findSpan(spans, "notifications/initialized", "SERVER").getJsonObject("attributes")
                        .getString("mcp.session.id"));

        // resources/list
        assertNotNull(findSpan(spans, "resources/list", "CLIENT"));
        assertParentedBy(spans, "resources/list");
        JsonObject listServer = findSpan(spans, "resources/list", "SERVER");
        assertEquals(mcpSessionId, listServer.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("resources/list", listServer.getJsonObject("attributes").getString("mcp.method.name"));

        // resources/read — both CLIENT and SERVER span names are "resources/read" (no URI in span name)
        assertNotNull(findSpan(spans, "resources/read", "CLIENT"));
        assertParentedBy(spans, "resources/read");
        JsonObject readServer = findSpan(spans, "resources/read", "SERVER");
        assertEquals(mcpSessionId, readServer.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("resources/read", readServer.getJsonObject("attributes").getString("mcp.method.name"));
        assertEquals("file:///project/alpha", readServer.getJsonObject("attributes").getString("mcp.resource.uri"));
    }

    @Test
    void testSampling() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setTracing(openTelemetry)
                .setClientCapabilities(new ClientCapability(ClientCapability.SAMPLING, Map.of()))
                .build()
                .connect();

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "samplingTool"));
        client.sendAndForget(request);

        // The server should send a sampling request
        JsonObject sr = client.waitForRequests(1).requests().get(0);
        assertEquals("sampling/createMessage", sr.getString("method"));
        Long id = sr.getLong("id");
        JsonObject response = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("result", new JsonObject()
                        .put("role", "assistant")
                        .put("model", "test-model")
                        .put("content", new JsonObject()
                                .put("type", "text")
                                .put("text", "Hello from sampling.")))
                .put("id", id);
        client.sendAndForget(response);

        JsonObject toolCallResponse = client.waitForResponse(request);
        JsonObject toolCallResult = toolCallResponse.getJsonObject("result");
        assertNotNull(toolCallResult);
        assertFalse(toolCallResult.getBoolean("isError"));
        JsonArray content = toolCallResult.getJsonArray("content");
        assertEquals(1, content.size());
        assertEquals("Hello from sampling.", content.getJsonObject(0).getString("text"));

        // 3 SERVER spans + 3 CLIENT spans = 6 minimum
        final JsonArray spans = awaitSpans(6);

        // Initialize
        assertNotNull(findSpan(spans, "initialize", "CLIENT"));
        assertParentedBy(spans, "initialize");
        final String mcpSessionId = findSpan(spans, "initialize", "SERVER")
                .getJsonObject("attributes").getString("mcp.session.id");
        assertNotNull(mcpSessionId);

        // Notification initialized
        assertNotNull(findSpan(spans, "notifications/initialized", "CLIENT"));
        assertParentedBy(spans, "notifications/initialized");
        assertEquals(mcpSessionId,
                findSpan(spans, "notifications/initialized", "SERVER").getJsonObject("attributes")
                        .getString("mcp.session.id"));

        // tools/call samplingTool
        assertNotNull(findSpan(spans, "tools/call", "CLIENT"));
        assertParentedBy(spans, "tools/call", "tools/call samplingTool");
        JsonObject span = findSpan(spans, "tools/call samplingTool");
        assertEquals(mcpSessionId, span.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("tools/call", span.getJsonObject("attributes").getString("mcp.method.name"));
        assertEquals("samplingTool", span.getJsonObject("attributes").getString("gen_ai.tool.name"));
    }

    @Test
    void testElicitation() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setTracing(openTelemetry)
                .setClientCapabilities(new ClientCapability(ClientCapability.ELICITATION, Map.of()))
                .build()
                .connect();

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "elicitationTool"));
        client.sendAndForget(request);

        // The server should send an elicitation request
        JsonObject er = client.waitForRequests(1).requests().get(0);
        assertEquals("elicitation/create", er.getString("method"));
        JsonObject params = er.getJsonObject("params");
        assertEquals("What's your name?", params.getString("message"));

        Long id = er.getLong("id");
        JsonObject response = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("result", new JsonObject()
                        .put("action", ElicitationResponse.Action.ACCEPT.toString().toLowerCase())
                        .put("content", new JsonObject()
                                .put("name", "Quarkus")))
                .put("id", id);
        client.sendAndForget(response);

        JsonObject toolCallResponse = client.waitForResponse(request);
        JsonObject toolCallResult = toolCallResponse.getJsonObject("result");
        assertNotNull(toolCallResult);
        assertFalse(toolCallResult.getBoolean("isError"));
        JsonArray content = toolCallResult.getJsonArray("content");
        assertEquals(1, content.size());
        assertEquals("Hello Quarkus!", content.getJsonObject(0).getString("text"));

        // 3 SERVER spans + 3 CLIENT spans = 6 minimum
        final JsonArray spans = awaitSpans(6);

        // Initialize
        assertNotNull(findSpan(spans, "initialize", "CLIENT"));
        assertParentedBy(spans, "initialize");
        final String mcpSessionId = findSpan(spans, "initialize", "SERVER")
                .getJsonObject("attributes").getString("mcp.session.id");
        assertNotNull(mcpSessionId);

        // Notification initialized
        assertNotNull(findSpan(spans, "notifications/initialized", "CLIENT"));
        assertParentedBy(spans, "notifications/initialized");
        assertEquals(mcpSessionId,
                findSpan(spans, "notifications/initialized", "SERVER").getJsonObject("attributes")
                        .getString("mcp.session.id"));

        // tools/call elicitationTool
        assertNotNull(findSpan(spans, "tools/call", "CLIENT"));
        assertParentedBy(spans, "tools/call", "tools/call elicitationTool");
        JsonObject span = findSpan(spans, "tools/call elicitationTool");
        assertEquals(mcpSessionId, span.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("tools/call", span.getJsonObject("attributes").getString("mcp.method.name"));
        assertEquals("elicitationTool", span.getJsonObject("attributes").getString("gen_ai.tool.name"));
    }

    /**
     * Asserts that the SERVER span named {@code spanName} is parented to the CLIENT span
     * with the same name, and that they share the same traceId.
     */
    private void assertParentedBy(JsonArray spans, String spanName) {
        assertParentedBy(spans, spanName, spanName);
    }

    /**
     * Asserts that the SERVER span named {@code serverSpanName} is parented to the CLIENT span
     * named {@code clientSpanName}, and that they share the same traceId.
     */
    private void assertParentedBy(JsonArray spans, String clientSpanName, String serverSpanName) {
        JsonObject clientSpan = findSpan(spans, clientSpanName, "CLIENT");
        JsonObject serverSpan;
        if (clientSpanName.equals(serverSpanName)) {
            serverSpan = findSpan(spans, serverSpanName, "SERVER");
        } else {
            serverSpan = findSpan(spans, serverSpanName);
        }
        assertEquals(clientSpan.getString("spanId"), serverSpan.getString("parent_spanId"),
                serverSpanName + " SERVER span should be parented to " + clientSpanName + " CLIENT span");
        assertEquals(clientSpan.getString("traceId"), serverSpan.getString("traceId"),
                serverSpanName + " should share traceId with client span");
    }

    private JsonArray awaitSpans(int minExpectedCount) {
        AtomicReference<JsonArray> holder = new AtomicReference<>();
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> {
                    JsonArray spans = new JsonArray(given()
                            .get("/spans")
                            .then()
                            .statusCode(200)
                            .extract().asString());
                    if (spans.size() >= minExpectedCount) {
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

    private JsonObject findSpan(JsonArray spans, String spanName, String kind) {
        for (int i = 0; i < spans.size(); i++) {
            JsonObject s = spans.getJsonObject(i);
            if (spanName.equals(s.getString("name")) && kind.equals(s.getString("kind"))) {
                return s;
            }
        }
        throw new AssertionError("Span not found: " + spanName + " (" + kind + ")");
    }
}
