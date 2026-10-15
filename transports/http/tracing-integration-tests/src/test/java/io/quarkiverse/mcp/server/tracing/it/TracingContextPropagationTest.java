package io.quarkiverse.mcp.server.tracing.it;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.OpenTelemetry;
import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.McpProtocolVersion;
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
                .setStateless()
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

        final JsonArray spans = awaitSpans(6);

        // Server discovery
        assertNotNull(findSpan(spans, "server/discover", "CLIENT"));
        assertParentedBy(spans, "server/discover");
        JsonObject discoverServer = findSpan(spans, "server/discover", "SERVER");
        assertTrue(
                McpProtocolVersion.isStateless(discoverServer.getJsonObject("attributes").getString("mcp.protocol.version")));
        assertNull(client.mcpSessionId());

        // tools/list
        assertNotNull(findSpan(spans, "tools/list", "CLIENT"));
        assertParentedBy(spans, "tools/list");
        JsonObject listServer = findSpan(spans, "tools/list", "SERVER");
        assertEquals("tools/list", listServer.getJsonObject("attributes").getString("mcp.method.name"));

        // tools/call — SERVER span name includes tool name; CLIENT span is just "tools/call"
        assertNotNull(findSpan(spans, "tools/call", "CLIENT"));
        assertParentedBy(spans, "tools/call", "tools/call toLowerCase");
        JsonObject callServer = findSpan(spans, "tools/call toLowerCase");
        assertEquals("tools/call", callServer.getJsonObject("attributes").getString("mcp.method.name"));
        assertEquals("toLowerCase", callServer.getJsonObject("attributes").getString("gen_ai.tool.name"));
    }

    @Test
    void testPrompt() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
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

        final JsonArray spans = awaitSpans(6);

        // Server discovery
        assertNotNull(findSpan(spans, "server/discover", "CLIENT"));
        assertParentedBy(spans, "server/discover");
        JsonObject discoverServer = findSpan(spans, "server/discover", "SERVER");
        assertTrue(
                McpProtocolVersion.isStateless(discoverServer.getJsonObject("attributes").getString("mcp.protocol.version")));
        assertNull(client.mcpSessionId());

        // prompts/list
        assertNotNull(findSpan(spans, "prompts/list", "CLIENT"));
        assertParentedBy(spans, "prompts/list");
        JsonObject listServer = findSpan(spans, "prompts/list", "SERVER");
        assertEquals("prompts/list", listServer.getJsonObject("attributes").getString("mcp.method.name"));

        // prompts/get — SERVER span name includes prompt name; CLIENT span is just "prompts/get"
        assertNotNull(findSpan(spans, "prompts/get", "CLIENT"));
        assertParentedBy(spans, "prompts/get", "prompts/get code_assist");
        JsonObject getServer = findSpan(spans, "prompts/get code_assist");
        assertEquals("prompts/get", getServer.getJsonObject("attributes").getString("mcp.method.name"));
        assertEquals("code_assist", getServer.getJsonObject("attributes").getString("gen_ai.prompt.name"));
    }

    @Test
    void testResource() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
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

        final JsonArray spans = awaitSpans(6);

        // Server discovery
        assertNotNull(findSpan(spans, "server/discover", "CLIENT"));
        assertParentedBy(spans, "server/discover");
        JsonObject discoverServer = findSpan(spans, "server/discover", "SERVER");
        assertTrue(
                McpProtocolVersion.isStateless(discoverServer.getJsonObject("attributes").getString("mcp.protocol.version")));
        assertNull(client.mcpSessionId());

        // resources/list
        assertNotNull(findSpan(spans, "resources/list", "CLIENT"));
        assertParentedBy(spans, "resources/list");
        JsonObject listServer = findSpan(spans, "resources/list", "SERVER");
        assertEquals("resources/list", listServer.getJsonObject("attributes").getString("mcp.method.name"));

        // resources/read — both CLIENT and SERVER span names are "resources/read" (no URI in span name)
        assertNotNull(findSpan(spans, "resources/read", "CLIENT"));
        assertParentedBy(spans, "resources/read");
        JsonObject readServer = findSpan(spans, "resources/read", "SERVER");
        assertEquals("resources/read", readServer.getJsonObject("attributes").getString("mcp.method.name"));
        assertEquals("file:///project/alpha", readServer.getJsonObject("attributes").getString("mcp.resource.uri"));
    }

    @Test
    void testSampling() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .setTracing(openTelemetry)
                .setClientCapabilities(new ClientCapability(ClientCapability.SAMPLING, Map.of()))
                .build()
                .connect();

        // Stateless servers request client input in the result, then consume it on retry.
        client.when()
                .toolsCall("samplingTool")
                .withRawAssert(response -> {
                    JsonObject result = response.getJsonObject("result");
                    assertEquals("input_required", result.getString("resultType"));
                    JsonObject inputRequest = result.getJsonObject("inputRequests").getJsonObject("llmInput");
                    assertEquals("sampling/createMessage", inputRequest.getString("method"));
                    JsonObject params = inputRequest.getJsonObject("params");
                    assertEquals(100, params.getInteger("maxTokens"));
                    assertEquals("What's happening?", params.getJsonArray("messages").getJsonObject(0)
                            .getJsonObject("content").getString("text"));
                })
                .send()
                .thenAssertResults();
        client.when()
                .toolsCall("samplingTool")
                .withInputResponses(new JsonObject().put("llmInput",
                        new JsonObject().put("role", "assistant").put("model", "test-model")
                                .put("content", new JsonObject().put("type", "text").put("text", "Hello from sampling."))))
                .withAssert(result -> {
                    assertFalse(result.isError());
                    assertEquals("Hello from sampling.", result.firstContent().asText().text());
                })
                .send()
                .thenAssertResults();

        final JsonArray spans = awaitSpans(6);

        // Server discovery
        assertNotNull(findSpan(spans, "server/discover", "CLIENT"));
        assertParentedBy(spans, "server/discover");

        // tools/call samplingTool
        assertNotNull(findSpan(spans, "tools/call", "CLIENT"));
        assertParentedBy(spans, "tools/call", "tools/call samplingTool");
        JsonObject span = findSpan(spans, "tools/call samplingTool");
        assertEquals("tools/call", span.getJsonObject("attributes").getString("mcp.method.name"));
        assertEquals("samplingTool", span.getJsonObject("attributes").getString("gen_ai.tool.name"));
    }

    @Test
    void testElicitation() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .setTracing(openTelemetry)
                .setClientCapabilities(new ClientCapability(ClientCapability.ELICITATION, Map.of()))
                .build()
                .connect();

        // Stateless servers request client input in the result, then consume it on retry.
        client.when()
                .toolsCall("elicitationTool")
                .withRawAssert(response -> {
                    JsonObject result = response.getJsonObject("result");
                    assertEquals("input_required", result.getString("resultType"));
                    JsonObject inputRequest = result.getJsonObject("inputRequests").getJsonObject("userInput");
                    assertEquals("elicitation/create", inputRequest.getString("method"));
                    JsonObject params = inputRequest.getJsonObject("params");
                    assertEquals("What's your name?", params.getString("message"));
                    assertEquals("string", params.getJsonObject("requestedSchema").getJsonObject("properties")
                            .getJsonObject("name").getString("type"));
                })
                .send()
                .thenAssertResults();
        client.when()
                .toolsCall("elicitationTool")
                .withInputResponses(new JsonObject().put("userInput", new JsonObject().put("action", "accept")
                        .put("content", new JsonObject().put("name", "Quarkus"))))
                .withAssert(result -> {
                    assertFalse(result.isError());
                    assertEquals("Hello Quarkus!", result.firstContent().asText().text());
                })
                .send()
                .thenAssertResults();

        final JsonArray spans = awaitSpans(6);

        // Server discovery
        assertNotNull(findSpan(spans, "server/discover", "CLIENT"));
        assertParentedBy(spans, "server/discover");

        // tools/call elicitationTool
        assertNotNull(findSpan(spans, "tools/call", "CLIENT"));
        assertParentedBy(spans, "tools/call", "tools/call elicitationTool");
        JsonObject span = findSpan(spans, "tools/call elicitationTool");
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
        int matched = 0;
        for (int i = 0; i < spans.size(); i++) {
            JsonObject serverSpan = spans.getJsonObject(i);
            if (!serverSpanName.equals(serverSpan.getString("name")) || !"SERVER".equals(serverSpan.getString("kind"))) {
                continue;
            }
            JsonObject clientSpan = null;
            for (int j = 0; j < spans.size(); j++) {
                JsonObject candidate = spans.getJsonObject(j);
                if (clientSpanName.equals(candidate.getString("name")) && "CLIENT".equals(candidate.getString("kind"))
                        && candidate.getString("spanId").equals(serverSpan.getString("parent_spanId"))) {
                    clientSpan = candidate;
                    break;
                }
            }
            assertNotNull(clientSpan, serverSpanName + " SERVER span should be parented to a CLIENT span");
            assertEquals(clientSpan.getString("traceId"), serverSpan.getString("traceId"));
            assertTrue(
                    McpProtocolVersion.isStateless(serverSpan.getJsonObject("attributes").getString("mcp.protocol.version")));
            matched++;
        }
        assertEquals("tools/call samplingTool".equals(serverSpanName) || "tools/call elicitationTool".equals(serverSpanName)
                ? 2
                : 1, matched);
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
        JsonArray spans = holder.get();
        for (int i = 0; i < spans.size(); i++) {
            JsonObject span = spans.getJsonObject(i);
            String name = span.getString("name");
            if ("SERVER".equals(span.getString("kind"))) {
                assertNull(span.getJsonObject("attributes").getString("mcp.session.id"));
            }
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
