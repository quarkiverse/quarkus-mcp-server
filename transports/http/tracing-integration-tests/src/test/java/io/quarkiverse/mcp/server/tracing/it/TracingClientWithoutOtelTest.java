package io.quarkiverse.mcp.server.tracing.it;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.ElicitationResponse;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
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
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
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

        final JsonArray spans = awaitSpans(4);

        // Initialize
        final JsonObject initialize = findSpan(spans, "initialize");
        assertNotNull(initialize);
        assertEquals("0000000000000000", initialize.getString("parent_spanId"));
        assertNotNull(initialize.getJsonObject("attributes").getString("mcp.session.id"));
        final String mcpSessionId = initialize.getJsonObject("attributes").getString("mcp.session.id");
        assertEquals(client.mcpSessionId(), mcpSessionId);
        assertEquals("http", initialize.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("tcp", initialize.getJsonObject("attributes").getString("network.transport"));

        // All spans have their own trace but have the same mcp.session.id.

        // Notification initialized
        final JsonObject notifInitialized = findSpan(spans, "notifications/initialized");
        assertNotNull(notifInitialized);
        assertEquals("0000000000000000", notifInitialized.getString("parent_spanId"));
        assertEquals(mcpSessionId, notifInitialized.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("http", notifInitialized.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("tcp", notifInitialized.getJsonObject("attributes").getString("network.transport"));

        // Verify prompts/list span
        JsonObject listSpan = findSpan(spans, "prompts/list");
        assertNotNull(listSpan);
        assertEquals("0000000000000000", listSpan.getString("parent_spanId"));
        assertEquals("SERVER", listSpan.getString("kind"));
        assertEquals(mcpSessionId, listSpan.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("prompts/list", listSpan.getJsonObject("attributes").getString("mcp.method.name"));
        assertNotNull(listSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
        assertNotNull(listSpan.getJsonObject("attributes").getString("mcp.protocol.version"));
        assertEquals("tcp", listSpan.getJsonObject("attributes").getString("network.transport"));
        assertEquals("http", listSpan.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("2", listSpan.getJsonObject("attributes").getString("network.protocol.version"));

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
        assertEquals("tcp", getSpan.getJsonObject("attributes").getString("network.transport"));
        assertEquals("http", getSpan.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("2", getSpan.getJsonObject("attributes").getString("network.protocol.version"));
    }

    @Test
    void testTool() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
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

        final JsonArray spans = awaitSpans(5);

        // Initialize
        final JsonObject initialize = findSpan(spans, "initialize");
        assertNotNull(initialize);
        assertEquals("0000000000000000", initialize.getString("parent_spanId"));
        assertNotNull(initialize.getJsonObject("attributes").getString("mcp.session.id"));
        final String mcpSessionId = initialize.getJsonObject("attributes").getString("mcp.session.id");
        assertEquals(client.mcpSessionId(), mcpSessionId);
        assertEquals("http", initialize.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("tcp", initialize.getJsonObject("attributes").getString("network.transport"));
        assertEquals("2", initialize.getJsonObject("attributes").getString("network.protocol.version"));

        // All spans have their own trace but have the same mcp.session.id.

        // Notification initialized
        final JsonObject notifInitialized = findSpan(spans, "notifications/initialized");
        assertNotNull(notifInitialized);
        assertEquals("0000000000000000", notifInitialized.getString("parent_spanId"));
        assertEquals(mcpSessionId, notifInitialized.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("http", notifInitialized.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("tcp", notifInitialized.getJsonObject("attributes").getString("network.transport"));
        assertEquals("2", notifInitialized.getJsonObject("attributes").getString("network.protocol.version"));

        // Verify tools/list span
        JsonObject listSpan = findSpan(spans, "tools/list");
        assertNotNull(listSpan);
        assertEquals("0000000000000000", listSpan.getString("parent_spanId"));
        assertEquals("SERVER", listSpan.getString("kind"));
        assertEquals(mcpSessionId, listSpan.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("tools/list", listSpan.getJsonObject("attributes").getString("mcp.method.name"));
        assertNotNull(listSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
        assertNotNull(listSpan.getJsonObject("attributes").getString("mcp.protocol.version"));
        assertEquals("tcp", listSpan.getJsonObject("attributes").getString("network.transport"));
        assertEquals("http", listSpan.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("2", listSpan.getJsonObject("attributes").getString("network.protocol.version"));

        // Verify tools/call toLowerCase span
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
        assertEquals("tcp", callSpan.getJsonObject("attributes").getString("network.transport"));
        assertEquals("http", callSpan.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("2", callSpan.getJsonObject("attributes").getString("network.protocol.version"));
        assertEquals("UNSET", callSpan.getString("statusCode"));

        // Verify tools/call answer span
        JsonObject answerSpan = findSpan(spans, "tools/call answer");
        assertNotNull(answerSpan);
        assertEquals("0000000000000000", answerSpan.getString("parent_spanId"));
        assertEquals("SERVER", answerSpan.getString("kind"));
        assertEquals(mcpSessionId, answerSpan.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("tools/call", answerSpan.getJsonObject("attributes").getString("mcp.method.name"));
        assertEquals("answer", answerSpan.getJsonObject("attributes").getString("gen_ai.tool.name"));
        assertEquals("execute_tool", answerSpan.getJsonObject("attributes").getString("gen_ai.operation.name"));
        assertNotNull(answerSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
        assertNotNull(answerSpan.getJsonObject("attributes").getString("mcp.protocol.version"));
        assertEquals("tcp", answerSpan.getJsonObject("attributes").getString("network.transport"));
        assertEquals("http", answerSpan.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("2", answerSpan.getJsonObject("attributes").getString("network.protocol.version"));
        assertEquals("UNSET", answerSpan.getString("statusCode"));
    }

    @Test
    void testToolError() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        // failingTool throws RuntimeException -> JSON-RPC internal error
        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "failingTool")
                        .put("arguments", new JsonObject().put("value", "test")));
        client.sendAndForget(request);

        final JsonArray spans = awaitSpans(3);

        // Initialize
        final JsonObject initialize = findSpan(spans, "initialize");
        assertNotNull(initialize);
        assertEquals("0000000000000000", initialize.getString("parent_spanId"));
        assertNotNull(initialize.getJsonObject("attributes").getString("mcp.session.id"));
        final String mcpSessionId = initialize.getJsonObject("attributes").getString("mcp.session.id");
        assertEquals(client.mcpSessionId(), mcpSessionId);
        assertEquals("http", initialize.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("tcp", initialize.getJsonObject("attributes").getString("network.transport"));

        // Notification initialized
        final JsonObject notifInitialized = findSpan(spans, "notifications/initialized");
        assertNotNull(notifInitialized);
        assertEquals("0000000000000000", notifInitialized.getString("parent_spanId"));
        assertEquals(mcpSessionId, notifInitialized.getJsonObject("attributes").getString("mcp.session.id"));

        // Verify tools/call failingTool span
        JsonObject span = findSpan(spans, "tools/call failingTool");
        assertNotNull(span);
        assertEquals("0000000000000000", span.getString("parent_spanId"));
        assertEquals("SERVER", span.getString("kind"));
        assertEquals(mcpSessionId, span.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("tools/call", span.getJsonObject("attributes").getString("mcp.method.name"));
        assertEquals("failingTool", span.getJsonObject("attributes").getString("gen_ai.tool.name"));
        assertEquals("execute_tool", span.getJsonObject("attributes").getString("gen_ai.operation.name"));
        assertNotNull(span.getJsonObject("attributes").getString("mcp.protocol.version"));
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
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
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

        final JsonArray spans = awaitSpans(4);

        // Initialize
        final JsonObject initialize = findSpan(spans, "initialize");
        assertNotNull(initialize);
        assertEquals("0000000000000000", initialize.getString("parent_spanId"));
        assertNotNull(initialize.getJsonObject("attributes").getString("mcp.session.id"));
        final String mcpSessionId = initialize.getJsonObject("attributes").getString("mcp.session.id");
        assertEquals(client.mcpSessionId(), mcpSessionId);
        assertEquals("http", initialize.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("tcp", initialize.getJsonObject("attributes").getString("network.transport"));

        // All spans have their own trace but have the same mcp.session.id.

        // Notification initialized
        final JsonObject notifInitialized = findSpan(spans, "notifications/initialized");
        assertNotNull(notifInitialized);
        assertEquals("0000000000000000", notifInitialized.getString("parent_spanId"));
        assertEquals(mcpSessionId, notifInitialized.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("http", notifInitialized.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("tcp", notifInitialized.getJsonObject("attributes").getString("network.transport"));

        // Verify resources/list span
        JsonObject listSpan = findSpan(spans, "resources/list");
        assertNotNull(listSpan);
        assertEquals("0000000000000000", listSpan.getString("parent_spanId"));
        assertEquals("SERVER", listSpan.getString("kind"));
        assertEquals(mcpSessionId, listSpan.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("resources/list", listSpan.getJsonObject("attributes").getString("mcp.method.name"));
        assertNotNull(listSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
        assertNotNull(listSpan.getJsonObject("attributes").getString("mcp.protocol.version"));
        assertEquals("tcp", listSpan.getJsonObject("attributes").getString("network.transport"));
        assertEquals("http", listSpan.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("2", listSpan.getJsonObject("attributes").getString("network.protocol.version"));

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
        assertEquals("tcp", readSpan.getJsonObject("attributes").getString("network.transport"));
        assertEquals("http", readSpan.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("2", readSpan.getJsonObject("attributes").getString("network.protocol.version"));
    }

    @Test
    void testSampling() throws InterruptedException {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
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

        final JsonArray spans = awaitSpans(3);

        // Initialize
        final JsonObject initialize = findSpan(spans, "initialize");
        assertNotNull(initialize);
        assertEquals("0000000000000000", initialize.getString("parent_spanId"));
        assertNotNull(initialize.getJsonObject("attributes").getString("mcp.session.id"));
        final String mcpSessionId = initialize.getJsonObject("attributes").getString("mcp.session.id");
        assertEquals("http", initialize.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("tcp", initialize.getJsonObject("attributes").getString("network.transport"));

        // Notification initialized
        final JsonObject notifInitialized = findSpan(spans, "notifications/initialized");
        assertNotNull(notifInitialized);
        assertEquals("0000000000000000", notifInitialized.getString("parent_spanId"));
        assertEquals(mcpSessionId, notifInitialized.getJsonObject("attributes").getString("mcp.session.id"));

        // Verify tools/call span
        JsonObject span = findSpan(spans, "tools/call samplingTool");
        assertNotNull(span);
        assertEquals("0000000000000000", span.getString("parent_spanId"));
        assertEquals("SERVER", span.getString("kind"));
        assertEquals(mcpSessionId, span.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("tools/call", span.getJsonObject("attributes").getString("mcp.method.name"));
        assertEquals("samplingTool", span.getJsonObject("attributes").getString("gen_ai.tool.name"));
        assertEquals("execute_tool", span.getJsonObject("attributes").getString("gen_ai.operation.name"));
        assertNotNull(span.getJsonObject("attributes").getString("mcp.protocol.version"));
        assertEquals("tcp", span.getJsonObject("attributes").getString("network.transport"));
        assertEquals("http", span.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("2", span.getJsonObject("attributes").getString("network.protocol.version"));
    }

    @Test
    void testElicitation() throws InterruptedException {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
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
        JsonObject schemaProperties = params.getJsonObject("requestedSchema").getJsonObject("properties");
        assertEquals("string", schemaProperties.getJsonObject("name").getString("type"));

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

        final JsonArray spans = awaitSpans(3);

        // Initialize
        final JsonObject initialize = findSpan(spans, "initialize");
        assertNotNull(initialize);
        assertEquals("0000000000000000", initialize.getString("parent_spanId"));
        assertNotNull(initialize.getJsonObject("attributes").getString("mcp.session.id"));
        final String mcpSessionId = initialize.getJsonObject("attributes").getString("mcp.session.id");
        assertEquals("http", initialize.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("tcp", initialize.getJsonObject("attributes").getString("network.transport"));

        // Notification initialized
        final JsonObject notifInitialized = findSpan(spans, "notifications/initialized");
        assertNotNull(notifInitialized);
        assertEquals("0000000000000000", notifInitialized.getString("parent_spanId"));
        assertEquals(mcpSessionId, notifInitialized.getJsonObject("attributes").getString("mcp.session.id"));

        // Verify tools/call span
        JsonObject span = findSpan(spans, "tools/call elicitationTool");
        assertNotNull(span);
        assertEquals("0000000000000000", span.getString("parent_spanId"));
        assertEquals("SERVER", span.getString("kind"));
        assertEquals(mcpSessionId, span.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("tools/call", span.getJsonObject("attributes").getString("mcp.method.name"));
        assertEquals("elicitationTool", span.getJsonObject("attributes").getString("gen_ai.tool.name"));
        assertEquals("execute_tool", span.getJsonObject("attributes").getString("gen_ai.operation.name"));
        assertNotNull(span.getJsonObject("attributes").getString("mcp.protocol.version"));
        assertEquals("tcp", span.getJsonObject("attributes").getString("network.transport"));
        assertEquals("http", span.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("2", span.getJsonObject("attributes").getString("network.protocol.version"));
    }

    @Test
    void testPing() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .pingPong()
                .thenAssertResults();

        final JsonArray spans = awaitSpans(3);

        // Initialize
        final JsonObject initialize = findSpan(spans, "initialize");
        assertNotNull(initialize);
        assertEquals("0000000000000000", initialize.getString("parent_spanId"));
        assertNotNull(initialize.getJsonObject("attributes").getString("mcp.session.id"));
        final String mcpSessionId = initialize.getJsonObject("attributes").getString("mcp.session.id");
        assertEquals(client.mcpSessionId(), mcpSessionId);
        assertEquals("http", initialize.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("tcp", initialize.getJsonObject("attributes").getString("network.transport"));

        // Notification initialized
        final JsonObject notifInitialized = findSpan(spans, "notifications/initialized");
        assertNotNull(notifInitialized);
        assertEquals("0000000000000000", notifInitialized.getString("parent_spanId"));
        assertEquals(mcpSessionId, notifInitialized.getJsonObject("attributes").getString("mcp.session.id"));

        // Verify ping span
        JsonObject span = findSpan(spans, "ping");
        assertNotNull(span);
        assertEquals("0000000000000000", span.getString("parent_spanId"));
        assertEquals("SERVER", span.getString("kind"));
        assertEquals(mcpSessionId, span.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("ping", span.getJsonObject("attributes").getString("mcp.method.name"));
        assertNotNull(span.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
        assertNotNull(span.getJsonObject("attributes").getString("mcp.protocol.version"));
        assertEquals("tcp", span.getJsonObject("attributes").getString("network.transport"));
        assertEquals("http", span.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("2", span.getJsonObject("attributes").getString("network.protocol.version"));
    }

    @Test
    void testInitialize() {
        // Simply connecting triggers an initialize request
        McpAssured.newConnectedStreamableClient();

        final JsonArray spans = awaitSpans(2);

        // Initialize
        JsonObject span = findSpan(spans, "initialize");
        assertNotNull(span);
        assertEquals("0000000000000000", span.getString("parent_spanId"));
        assertEquals("SERVER", span.getString("kind"));
        assertEquals("initialize", span.getJsonObject("attributes").getString("mcp.method.name"));
        assertNotNull(span.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
        // mcp.protocol.version is not set during initialize -- it's being negotiated in this request

        // Notification initialized
        JsonObject notifInitialized = findSpan(spans, "notifications/initialized");
        assertNotNull(notifInitialized);
        assertEquals("0000000000000000", notifInitialized.getString("parent_spanId"));
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
