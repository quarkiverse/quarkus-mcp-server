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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.PromptInfo;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@QuarkusTest
@TestProfile(TracingClientWithoutOtelTest.OTelEnabledProfile.class)
class TracingClientWithoutOtelTest {

    @BeforeEach
    void resetSpans() {
        given().delete("/spans").then().statusCode(204);
    }

    @Test
    void testPrompt() {
        McpStreamableTestClient client = McpAssured.newStreamableClient().setStateless()
                .build().connect();
        client.when()
                .promptsList(p -> {
                    assertEquals(1, p.size());
                    PromptInfo codeAssist = p.findByName("code_assist");
                    assertEquals(1, codeAssist.arguments().size());
                    assertEquals("lang", codeAssist.arguments().get(0).name());
                    assertTrue(codeAssist.arguments().get(0).required());
                })
                .promptsGet("code_assist", Map.of("lang", "java"), r -> {
                    assertEquals("System.out.println(\"Hello world!\");", r.messages().get(0).content().asText().text());
                })
                .thenAssertResults();

        final JsonArray spans = awaitSpans(3);

        // Server discovery
        final JsonObject discover = findSpan(spans, "server/discover");
        assertNotNull(discover);
        assertEquals("0000000000000000", discover.getString("parent_spanId"));
        assertNull(client.mcpSessionId());
        assertEquals("http", discover.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("tcp", discover.getJsonObject("attributes").getString("network.transport"));

        // Stateless requests do not establish a protocol session.

        // Verify prompts/list span
        JsonObject listSpan = findSpan(spans, "prompts/list");
        assertNotNull(listSpan);
        assertEquals("0000000000000000", listSpan.getString("parent_spanId"));
        assertEquals("SERVER", listSpan.getString("kind"));
        assertEquals("prompts/list", listSpan.getJsonObject("attributes").getString("mcp.method.name"));
        assertNotNull(listSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
        assertTrue(McpProtocolVersion.isStateless(listSpan.getJsonObject("attributes").getString("mcp.protocol.version")));
        assertEquals("tcp", listSpan.getJsonObject("attributes").getString("network.transport"));
        assertEquals("http", listSpan.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("2", listSpan.getJsonObject("attributes").getString("network.protocol.version"));

        // Verify prompts/get span
        JsonObject getSpan = findSpan(spans, "prompts/get code_assist");
        assertNotNull(getSpan);
        assertEquals("0000000000000000", getSpan.getString("parent_spanId"));
        assertEquals("SERVER", getSpan.getString("kind"));
        assertEquals("prompts/get", getSpan.getJsonObject("attributes").getString("mcp.method.name"));
        assertEquals("code_assist", getSpan.getJsonObject("attributes").getString("gen_ai.prompt.name"));
        assertNotNull(getSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
        assertTrue(McpProtocolVersion.isStateless(getSpan.getJsonObject("attributes").getString("mcp.protocol.version")));
        assertEquals("tcp", getSpan.getJsonObject("attributes").getString("network.transport"));
        assertEquals("http", getSpan.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("2", getSpan.getJsonObject("attributes").getString("network.protocol.version"));
    }

    @Test
    void testTool() {
        McpStreamableTestClient client = McpAssured.newStreamableClient().setStateless()
                .build().connect();
        client.when()
                .toolsList(p -> {
                    assertEquals(5, p.size());
                    JsonObject schema = p.findByName("toLowerCase").inputSchema();
                    JsonObject properties = schema.getJsonObject("properties");
                    assertEquals(1, properties.size());
                    JsonObject valueProperty = properties.getJsonObject("value");
                    assertNotNull(valueProperty);
                    assertEquals("string", valueProperty.getString("type"));
                    assertNotNull(p.findByName("answer"));
                })
                .toolsCall("toLowerCase", Map.of("value", "LooP"), r -> {
                    assertEquals("loop", r.firstContent().asText().text());
                })
                .toolsCall("answer", Map.of("question", "Are you ok?"), r -> {
                    assertEquals("{\"value\":\"are you ok?\"}", r.firstContent().asText().text());
                })
                .thenAssertResults();

        final JsonArray spans = awaitSpans(4);

        // Server discovery
        final JsonObject discover = findSpan(spans, "server/discover");
        assertNotNull(discover);
        assertEquals("0000000000000000", discover.getString("parent_spanId"));
        assertNull(client.mcpSessionId());
        assertEquals(McpServer.DEFAULT, discover.getJsonObject("attributes").getString("mcp.server.name"));
        assertEquals("http", discover.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("tcp", discover.getJsonObject("attributes").getString("network.transport"));
        assertEquals("2", discover.getJsonObject("attributes").getString("network.protocol.version"));

        // Stateless requests do not establish a protocol session.

        // Verify tools/list span
        JsonObject listSpan = findSpan(spans, "tools/list");
        assertNotNull(listSpan);
        assertEquals("0000000000000000", listSpan.getString("parent_spanId"));
        assertEquals("SERVER", listSpan.getString("kind"));
        assertEquals(McpServer.DEFAULT, listSpan.getJsonObject("attributes").getString("mcp.server.name"));
        assertEquals("tools/list", listSpan.getJsonObject("attributes").getString("mcp.method.name"));
        assertNotNull(listSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
        assertTrue(McpProtocolVersion.isStateless(listSpan.getJsonObject("attributes").getString("mcp.protocol.version")));
        assertEquals("tcp", listSpan.getJsonObject("attributes").getString("network.transport"));
        assertEquals("http", listSpan.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("2", listSpan.getJsonObject("attributes").getString("network.protocol.version"));

        // Verify tools/call toLowerCase span
        JsonObject callSpan = findSpan(spans, "tools/call toLowerCase");
        assertNotNull(callSpan);
        assertEquals("0000000000000000", callSpan.getString("parent_spanId"));
        assertEquals("SERVER", callSpan.getString("kind"));
        assertEquals(McpServer.DEFAULT, callSpan.getJsonObject("attributes").getString("mcp.server.name"));
        assertEquals("tools/call", callSpan.getJsonObject("attributes").getString("mcp.method.name"));
        assertEquals("toLowerCase", callSpan.getJsonObject("attributes").getString("gen_ai.tool.name"));
        assertEquals("execute_tool", callSpan.getJsonObject("attributes").getString("gen_ai.operation.name"));
        assertNotNull(callSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
        assertTrue(McpProtocolVersion.isStateless(callSpan.getJsonObject("attributes").getString("mcp.protocol.version")));
        assertEquals("tcp", callSpan.getJsonObject("attributes").getString("network.transport"));
        assertEquals("http", callSpan.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("2", callSpan.getJsonObject("attributes").getString("network.protocol.version"));
        assertEquals("UNSET", callSpan.getString("statusCode"));

        // Verify tools/call answer span
        JsonObject answerSpan = findSpan(spans, "tools/call answer");
        assertNotNull(answerSpan);
        assertEquals("0000000000000000", answerSpan.getString("parent_spanId"));
        assertEquals("SERVER", answerSpan.getString("kind"));
        assertEquals(McpServer.DEFAULT, answerSpan.getJsonObject("attributes").getString("mcp.server.name"));
        assertEquals("tools/call", answerSpan.getJsonObject("attributes").getString("mcp.method.name"));
        assertEquals("answer", answerSpan.getJsonObject("attributes").getString("gen_ai.tool.name"));
        assertEquals("execute_tool", answerSpan.getJsonObject("attributes").getString("gen_ai.operation.name"));
        assertNotNull(answerSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
        assertTrue(McpProtocolVersion.isStateless(answerSpan.getJsonObject("attributes").getString("mcp.protocol.version")));
        assertEquals("tcp", answerSpan.getJsonObject("attributes").getString("network.transport"));
        assertEquals("http", answerSpan.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("2", answerSpan.getJsonObject("attributes").getString("network.protocol.version"));
        assertEquals("UNSET", answerSpan.getString("statusCode"));
    }

    @Test
    void testToolError() {
        McpStreamableTestClient client = McpAssured.newStreamableClient().setStateless()
                .build().connect();

        // failingTool throws RuntimeException -> JSON-RPC internal error
        client.when()
                .toolsCall("failingTool")
                .withArguments(Map.of("value", "test"))
                .withErrorAssert(error -> assertEquals(JsonRpcErrorCodes.INTERNAL_ERROR, error.code()))
                .send()
                .thenAssertResults();

        final JsonArray spans = awaitSpans(2);

        // Server discovery
        final JsonObject discover = findSpan(spans, "server/discover");
        assertNotNull(discover);
        assertEquals("0000000000000000", discover.getString("parent_spanId"));
        assertNull(client.mcpSessionId());
        assertEquals("http", discover.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("tcp", discover.getJsonObject("attributes").getString("network.transport"));

        // Verify tools/call failingTool span
        JsonObject span = findSpan(spans, "tools/call failingTool");
        assertNotNull(span);
        assertEquals("0000000000000000", span.getString("parent_spanId"));
        assertEquals("SERVER", span.getString("kind"));
        assertEquals("tools/call", span.getJsonObject("attributes").getString("mcp.method.name"));
        assertEquals("failingTool", span.getJsonObject("attributes").getString("gen_ai.tool.name"));
        assertEquals("execute_tool", span.getJsonObject("attributes").getString("gen_ai.operation.name"));
        assertTrue(McpProtocolVersion.isStateless(span.getJsonObject("attributes").getString("mcp.protocol.version")));
        assertEquals("tcp", span.getJsonObject("attributes").getString("network.transport"));
        assertEquals("http", span.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("2", span.getJsonObject("attributes").getString("network.protocol.version"));
        assertEquals("ERROR", span.getString("statusCode"));
        assertEquals(String.valueOf(JsonRpcErrorCodes.INTERNAL_ERROR),
                span.getJsonObject("attributes").getString("rpc.jsonrpc.error_code"));
        assertNotNull(span.getJsonObject("attributes").getString("rpc.jsonrpc.error_message"));
    }

    @Test
    void testResource() {
        McpStreamableTestClient client = McpAssured.newStreamableClient().setStateless()
                .build().connect();
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

        final JsonArray spans = awaitSpans(3);

        // Server discovery
        final JsonObject discover = findSpan(spans, "server/discover");
        assertNotNull(discover);
        assertEquals("0000000000000000", discover.getString("parent_spanId"));
        assertNull(client.mcpSessionId());
        assertEquals("http", discover.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("tcp", discover.getJsonObject("attributes").getString("network.transport"));

        // Stateless requests do not establish a protocol session.

        // Verify resources/list span
        JsonObject listSpan = findSpan(spans, "resources/list");
        assertNotNull(listSpan);
        assertEquals("0000000000000000", listSpan.getString("parent_spanId"));
        assertEquals("SERVER", listSpan.getString("kind"));
        assertEquals("resources/list", listSpan.getJsonObject("attributes").getString("mcp.method.name"));
        assertNotNull(listSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
        assertTrue(McpProtocolVersion.isStateless(listSpan.getJsonObject("attributes").getString("mcp.protocol.version")));
        assertEquals("tcp", listSpan.getJsonObject("attributes").getString("network.transport"));
        assertEquals("http", listSpan.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("2", listSpan.getJsonObject("attributes").getString("network.protocol.version"));

        // Verify resources/read span
        JsonObject readSpan = findSpan(spans, "resources/read");
        assertNotNull(readSpan);
        assertEquals("0000000000000000", readSpan.getString("parent_spanId"));
        assertEquals("SERVER", readSpan.getString("kind"));
        assertEquals("resources/read", readSpan.getJsonObject("attributes").getString("mcp.method.name"));
        assertEquals("file:///project/alpha", readSpan.getJsonObject("attributes").getString("mcp.resource.uri"));
        assertNotNull(readSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
        assertTrue(McpProtocolVersion.isStateless(readSpan.getJsonObject("attributes").getString("mcp.protocol.version")));
        assertEquals("tcp", readSpan.getJsonObject("attributes").getString("network.transport"));
        assertEquals("http", readSpan.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("2", readSpan.getJsonObject("attributes").getString("network.protocol.version"));
    }

    @Test
    void testSampling() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
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

        final JsonArray spans = awaitSpans(3);

        // Server discovery
        final JsonObject discover = findSpan(spans, "server/discover");
        assertNotNull(discover);
        assertEquals("0000000000000000", discover.getString("parent_spanId"));
        assertEquals("http", discover.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("tcp", discover.getJsonObject("attributes").getString("network.transport"));

        // Verify tools/call span
        int calls = 0;
        for (int i = 0; i < spans.size(); i++) {
            JsonObject span = spans.getJsonObject(i);
            if (!"tools/call samplingTool".equals(span.getString("name"))) {
                continue;
            }
            assertNotNull(span);
            assertEquals("0000000000000000", span.getString("parent_spanId"));
            assertEquals("SERVER", span.getString("kind"));
            assertEquals("tools/call", span.getJsonObject("attributes").getString("mcp.method.name"));
            assertEquals("samplingTool", span.getJsonObject("attributes").getString("gen_ai.tool.name"));
            assertEquals("execute_tool", span.getJsonObject("attributes").getString("gen_ai.operation.name"));
            assertTrue(McpProtocolVersion.isStateless(span.getJsonObject("attributes").getString("mcp.protocol.version")));
            assertEquals("tcp", span.getJsonObject("attributes").getString("network.transport"));
            assertEquals("http", span.getJsonObject("attributes").getString("network.protocol.name"));
            assertEquals("2", span.getJsonObject("attributes").getString("network.protocol.version"));
            assertEquals("UNSET", span.getString("statusCode"));
            calls++;
        }
        assertEquals(2, calls);
    }

    @Test
    void testElicitation() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
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

        final JsonArray spans = awaitSpans(3);

        // Server discovery
        final JsonObject discover = findSpan(spans, "server/discover");
        assertNotNull(discover);
        assertEquals("0000000000000000", discover.getString("parent_spanId"));
        assertEquals("http", discover.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("tcp", discover.getJsonObject("attributes").getString("network.transport"));

        // Verify tools/call span
        int calls = 0;
        for (int i = 0; i < spans.size(); i++) {
            JsonObject span = spans.getJsonObject(i);
            if (!"tools/call elicitationTool".equals(span.getString("name"))) {
                continue;
            }
            assertNotNull(span);
            assertEquals("0000000000000000", span.getString("parent_spanId"));
            assertEquals("SERVER", span.getString("kind"));
            assertEquals("tools/call", span.getJsonObject("attributes").getString("mcp.method.name"));
            assertEquals("elicitationTool", span.getJsonObject("attributes").getString("gen_ai.tool.name"));
            assertEquals("execute_tool", span.getJsonObject("attributes").getString("gen_ai.operation.name"));
            assertTrue(McpProtocolVersion.isStateless(span.getJsonObject("attributes").getString("mcp.protocol.version")));
            assertEquals("tcp", span.getJsonObject("attributes").getString("network.transport"));
            assertEquals("http", span.getJsonObject("attributes").getString("network.protocol.name"));
            assertEquals("2", span.getJsonObject("attributes").getString("network.protocol.version"));
            assertEquals("UNSET", span.getString("statusCode"));
            calls++;
        }
        assertEquals(2, calls);
    }

    @Test
    void testServerDiscover() {
        // Stateless connection discovers the server without initializing a session
        McpAssured.newStreamableClient().setStateless().build().connect();

        final JsonArray spans = awaitSpans(1);

        // Server discovery
        JsonObject span = findSpan(spans, "server/discover");
        assertNotNull(span);
        assertEquals("0000000000000000", span.getString("parent_spanId"));
        assertEquals("SERVER", span.getString("kind"));
        assertEquals("server/discover", span.getJsonObject("attributes").getString("mcp.method.name"));
        assertNotNull(span.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
        assertTrue(McpProtocolVersion.isStateless(span.getJsonObject("attributes").getString("mcp.protocol.version")));
    }

    private JsonArray awaitSpans(int expectedCount) {
        AtomicReference<JsonArray> holder = new AtomicReference<>();
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> {
                    JsonArray spans = new JsonArray(given()
                            .get("/spans")
                            .then()
                            .statusCode(200)
                            .extract().asString());
                    if (spans.size() >= expectedCount) {
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

    public static class OTelEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.otel.sdk.disabled", "false",
                    "quarkus.otel.bsp.schedule.delay", "100ms",
                    "quarkus.mcp.server.tracing.enabled", "true");
        }
    }
}
