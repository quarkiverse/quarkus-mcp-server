package io.quarkiverse.mcp.server.tracing.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.json.JsonArray;

@QuarkusTest
@TestProfile(TracingRuntimeDisabledTest.OtelRuntimeDisabledProfile.class)
class TracingRuntimeDisabledTest {

    @Test
    void testToolsCallNoSpanWhenOtelDisabled() {
        McpStreamableTestClient client = McpAssured.newStreamableClient().setStateless()
                .build().connect();
        client.when()
                .toolsCall("toLowerCase", Map.of("value", "HELLO"), r -> {
                    assertEquals("hello", r.content().get(0).asText().text());
                })
                .thenAssertResults();

        assertNoSpans();
    }

    @Test
    void testToolsCallErrorNoNpe() {
        McpStreamableTestClient client = McpAssured.newStreamableClient().setStateless()
                .build().connect();
        client.when()
                .toolsCall("failingTool")
                .withArguments(Map.of("value", "test"))
                .withErrorAssert(error -> assertEquals(JsonRpcErrorCodes.INTERNAL_ERROR, error.code()))
                .send()
                .thenAssertResults();

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
