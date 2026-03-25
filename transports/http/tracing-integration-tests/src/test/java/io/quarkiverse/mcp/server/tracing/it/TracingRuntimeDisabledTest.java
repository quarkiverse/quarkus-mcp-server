package io.quarkiverse.mcp.server.tracing.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@QuarkusTest
@TestProfile(TracingRuntimeDisabledTest.OtelRuntimeDisabledProfile.class)
class TracingRuntimeDisabledTest {

    @Test
    void testToolsCallNoSpanWhenOtelDisabled() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("toLowerCase", Map.of("value", "HELLO"), r -> {
                    assertEquals("hello", r.content().get(0).asText().text());
                })
                .thenAssertResults();

        assertNoSpans();
    }

    @Test
    void testToolsCallErrorNoNpe() throws InterruptedException {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        // failingTool throws RuntimeException — must not cause NPE in tracing code
        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "failingTool")
                        .put("arguments", new JsonObject().put("value", "test")));
        assertDoesNotThrow(() -> {
            client.sendAndForget(request);
            Thread.sleep(1000);
        });

        assertNoSpans();
    }

    private void assertNoSpans() {
        JsonArray spans = new JsonArray(given().get("/spans").then().statusCode(200)
                .extract().asString());
        assertTrue(spans.isEmpty(),
                "Expected no spans when OTel is disabled, but found: " + spans);
    }

    public static class OtelRuntimeDisabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.otel.sdk.disabled", "true");
        }
    }
}
