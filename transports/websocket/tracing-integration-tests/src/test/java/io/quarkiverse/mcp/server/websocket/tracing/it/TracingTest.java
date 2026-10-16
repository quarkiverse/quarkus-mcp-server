package io.quarkiverse.mcp.server.websocket.tracing.it;

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

import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpWebSocketTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.PromptInfo;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@QuarkusTest
@TestProfile(TracingTest.OTelEnabledProfile.class)
class TracingTest {

    @BeforeEach
    void resetSpans() {
        given().delete("/spans").then().statusCode(204);
    }

    @Test
    void testPrompt() {
        McpWebSocketTestClient client = McpAssured.newWebSocketClient().setProtocolVersion(McpProtocolVersion.V_2026_07_28)
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

        final JsonArray spans = awaitSpans(5);

        // Parent. From http server
        final JsonObject httpMcpWs = findSpan(spans, "GET /mcp/ws");
        assertNotNull(httpMcpWs);
        assertEquals("0000000000000000", httpMcpWs.getString("parent_spanId"));

        // Child. From websocket upgrade
        final JsonObject websocketMcpWs = findSpan(spans, "OPEN /mcp/ws");
        assertNotNull(websocketMcpWs);
        assertEquals(httpMcpWs.getString("spanId"), websocketMcpWs.getString("parent_spanId"));

        // Server discovery
        final JsonObject discover = findSpan(spans, "server/discover");
        assertNotNull(discover);
        assertEquals("0000000000000000", discover.getString("parent_spanId"));
        assertEquals("SERVER", discover.getString("kind"));
        assertEquals("server/discover", discover.getJsonObject("attributes").getString("mcp.method.name"));
        assertNull(discover.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("2026-07-28", discover.getJsonObject("attributes").getString("mcp.protocol.version"));
        assertEquals("websocket", discover.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("tcp", discover.getJsonObject("attributes").getString("network.transport"));

        // Verify prompts/list span
        JsonObject listSpan = findSpan(spans, "prompts/list");
        assertNotNull(listSpan);
        assertEquals("0000000000000000", listSpan.getString("parent_spanId"));
        assertEquals("SERVER", listSpan.getString("kind"));
        assertNull(listSpan.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("prompts/list", listSpan.getJsonObject("attributes").getString("mcp.method.name"));
        assertNotNull(listSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
        assertEquals("2026-07-28", listSpan.getJsonObject("attributes").getString("mcp.protocol.version"));
        assertEquals("tcp", listSpan.getJsonObject("attributes").getString("network.transport"));
        assertEquals("websocket", listSpan.getJsonObject("attributes").getString("network.protocol.name"));
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
        assertEquals("tcp", getSpan.getJsonObject("attributes").getString("network.transport"));
        assertEquals("websocket", getSpan.getJsonObject("attributes").getString("network.protocol.name"));
        assertNull(getSpan.getJsonObject("attributes").getString("network.protocol.version"));
    }

    @Test
    void testTool() {
        McpWebSocketTestClient client = McpAssured.newWebSocketClient().setProtocolVersion(McpProtocolVersion.V_2026_07_28)
                .build().connect();
        client.when()
                .toolsList(p -> {
                    assertEquals(1, p.size());
                    JsonObject schema = p.findByName("toLowerCase").inputSchema();
                    JsonObject properties = schema.getJsonObject("properties");
                    assertEquals(1, properties.size());
                    JsonObject valueProperty = properties.getJsonObject("value");
                    assertNotNull(valueProperty);
                    assertEquals("string", valueProperty.getString("type"));
                })
                .toolsCall("toLowerCase", Map.of("value", "LooP"), r -> {
                    assertEquals("loop", r.firstContent().asText().text());
                })
                .thenAssertResults();

        final JsonArray spans = awaitSpans(5);

        // Parent. From http server
        final JsonObject httpMcpWs = findSpan(spans, "GET /mcp/ws");
        assertNotNull(httpMcpWs);
        assertEquals("0000000000000000", httpMcpWs.getString("parent_spanId"));

        // Child. From websocket upgrade
        final JsonObject websocketMcpWs = findSpan(spans, "OPEN /mcp/ws");
        assertNotNull(websocketMcpWs);
        assertEquals(httpMcpWs.getString("spanId"), websocketMcpWs.getString("parent_spanId"));

        // Server discovery
        final JsonObject discover = findSpan(spans, "server/discover");
        assertNotNull(discover);
        assertEquals("0000000000000000", discover.getString("parent_spanId"));
        assertEquals("SERVER", discover.getString("kind"));
        assertEquals("server/discover", discover.getJsonObject("attributes").getString("mcp.method.name"));
        assertNull(discover.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("2026-07-28", discover.getJsonObject("attributes").getString("mcp.protocol.version"));
        assertEquals("websocket", discover.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("tcp", discover.getJsonObject("attributes").getString("network.transport"));

        // Verify tools/list span
        JsonObject listSpan = findSpan(spans, "tools/list");
        assertNotNull(listSpan);
        assertEquals("0000000000000000", listSpan.getString("parent_spanId"));
        assertEquals("SERVER", listSpan.getString("kind"));
        assertNull(listSpan.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("tools/list", listSpan.getJsonObject("attributes").getString("mcp.method.name"));
        assertNotNull(listSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
        assertEquals("2026-07-28", listSpan.getJsonObject("attributes").getString("mcp.protocol.version"));
        assertEquals("tcp", listSpan.getJsonObject("attributes").getString("network.transport"));
        assertEquals("websocket", listSpan.getJsonObject("attributes").getString("network.protocol.name"));
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
        assertEquals("tcp", callSpan.getJsonObject("attributes").getString("network.transport"));
        assertEquals("websocket", callSpan.getJsonObject("attributes").getString("network.protocol.name"));
        assertNull(callSpan.getJsonObject("attributes").getString("network.protocol.version"));
        assertEquals("UNSET", callSpan.getString("statusCode"));
    }

    @Test
    void testResource() {
        McpWebSocketTestClient client = McpAssured.newWebSocketClient().setProtocolVersion(McpProtocolVersion.V_2026_07_28)
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

        final JsonArray spans = awaitSpans(5);

        // Parent. From http server
        final JsonObject httpMcpWs = findSpan(spans, "GET /mcp/ws");
        assertNotNull(httpMcpWs);
        assertEquals("0000000000000000", httpMcpWs.getString("parent_spanId"));

        // Child. From websocket upgrade
        final JsonObject websocketMcpWs = findSpan(spans, "OPEN /mcp/ws");
        assertNotNull(websocketMcpWs);
        assertEquals(httpMcpWs.getString("spanId"), websocketMcpWs.getString("parent_spanId"));

        // Server discovery
        final JsonObject discover = findSpan(spans, "server/discover");
        assertNotNull(discover);
        assertEquals("0000000000000000", discover.getString("parent_spanId"));
        assertEquals("SERVER", discover.getString("kind"));
        assertEquals("server/discover", discover.getJsonObject("attributes").getString("mcp.method.name"));
        assertNull(discover.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("2026-07-28", discover.getJsonObject("attributes").getString("mcp.protocol.version"));
        assertEquals("websocket", discover.getJsonObject("attributes").getString("network.protocol.name"));
        assertEquals("tcp", discover.getJsonObject("attributes").getString("network.transport"));

        // Verify resources/list span
        JsonObject listSpan = findSpan(spans, "resources/list");
        assertNotNull(listSpan);
        assertEquals("0000000000000000", listSpan.getString("parent_spanId"));
        assertEquals("SERVER", listSpan.getString("kind"));
        assertNull(listSpan.getJsonObject("attributes").getString("mcp.session.id"));
        assertEquals("resources/list", listSpan.getJsonObject("attributes").getString("mcp.method.name"));
        assertNotNull(listSpan.getJsonObject("attributes").getString("rpc.jsonrpc.request_id"));
        assertEquals("2026-07-28", listSpan.getJsonObject("attributes").getString("mcp.protocol.version"));
        assertEquals("tcp", listSpan.getJsonObject("attributes").getString("network.transport"));
        assertEquals("websocket", listSpan.getJsonObject("attributes").getString("network.protocol.name"));
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
        assertEquals("tcp", readSpan.getJsonObject("attributes").getString("network.transport"));
        assertEquals("websocket", readSpan.getJsonObject("attributes").getString("network.protocol.name"));
        assertNull(readSpan.getJsonObject("attributes").getString("network.protocol.version"));
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

    public static class OTelEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.otel.bsp.schedule.delay", "100ms",
                    "quarkus.mcp.server.tracing.enabled", "true");
        }
    }
}
