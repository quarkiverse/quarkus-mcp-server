package io.quarkiverse.mcp.server.test.stateless;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.json.JsonObject;

class RestrictedLegacyProtocolVersionsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.supported-protocol-versions", "2026-07-28,2025-11-25");

    @Inject
    ConnectionManager connectionManager;

    @Test
    void permitsConfiguredLegacyInitializationWithoutVersionHeader() {
        request().body(initialize("2025-11-25").encode()).post("/mcp").then()
                .statusCode(200).header("Mcp-Session-Id", notNullValue())
                .body("result.protocolVersion", equalTo("2025-11-25"));
    }

    @Test
    void rejectsUnsupportedInitializationVersion() {
        request().body(initialize("2025-03-26").encode()).post("/mcp").then()
                .statusCode(400).header("Mcp-Session-Id", nullValue())
                .body("error.code", equalTo(-32022))
                .body("error.data.supported", contains("2026-07-28", "2025-11-25"));
    }

    @Test
    void rejectsUnknownLegacyInitializationVersion() {
        request().body(initialize("2025-09-01").encode()).post("/mcp").then()
                .statusCode(400).header("Mcp-Session-Id", nullValue())
                .body("error.code", equalTo(-32022))
                .body("error.data.requested", equalTo("2025-09-01"))
                .body("error.data.supported", contains("2026-07-28", "2025-11-25"));
    }

    @Test
    void allowedInitializeBodyCannotOverrideADisallowedHeader() {
        request().header("MCP-Protocol-Version", "2025-03-26")
                .body(initialize("2025-11-25").encode()).post("/mcp").then()
                .statusCode(400).header("Mcp-Session-Id", nullValue())
                .body("error.code", equalTo(-32022))
                .body("error.data.requested", equalTo("2025-03-26"));
    }

    @Test
    void allowedHeaderCannotOverrideADisallowedInitializeBody() {
        request().header("MCP-Protocol-Version", "2025-11-25")
                .body(initialize("2025-03-26").encode()).post("/mcp").then()
                .statusCode(400).header("Mcp-Session-Id", nullValue())
                .body("error.code", equalTo(-32022))
                .body("error.data.requested", equalTo("2025-03-26"));
    }

    @ParameterizedTest
    @CsvSource({
            "GET,2025-03-26,400",
            "DELETE,2025-03-26,400",
            "GET,2025-09-01,400",
            "DELETE,2025-09-01,400",
            "GET,2099-01-01,400",
            "DELETE,2099-01-01,400",
            "GET,,400",
            "DELETE,,400",
            "GET,2025-11-25,200",
            "DELETE,2025-11-25,200",
            "GET,2026-07-28,405",
            "DELETE,2026-07-28,405"
    })
    void sessionOperationsHonorConfiguredVersions(String method, String version, int expectedStatus) throws Exception {
        String sessionId = request().body(initialize("2025-11-25").encode()).post("/mcp").then()
                .statusCode(200).extract().header("Mcp-Session-Id");
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(testUri.resolve("/mcp"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "text/event-stream")
                    .header("Mcp-Session-Id", sessionId)
                    .method(method, HttpRequest.BodyPublishers.noBody());
            if (version != null) {
                builder.header("MCP-Protocol-Version", version);
            }
            HttpResponse<java.io.InputStream> response = HttpClient.newHttpClient()
                    .send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                assertEquals(expectedStatus, response.statusCode());
                if ("GET".equals(method) && expectedStatus == 200) {
                    assertEquals("text/event-stream", response.headers().firstValue("Content-Type").orElseThrow());
                }
            }
            if ("DELETE".equals(method) && expectedStatus == 200) {
                assertFalse(connectionManager.has(sessionId));
            } else {
                assertTrue(connectionManager.has(sessionId));
            }
        } finally {
            connectionManager.remove(sessionId);
        }
    }

    private static RequestSpecification request() {
        return given().contentType("application/json").accept("application/json, text/event-stream")
                .header("Mcp-Method", "initialize");
    }

    private static JsonObject initialize(String version) {
        return new JsonObject().put("jsonrpc", "2.0").put("id", 1).put("method", "initialize")
                .put("params", new JsonObject().put("protocolVersion", version)
                        .put("capabilities", new JsonObject())
                        .put("clientInfo", new JsonObject().put("name", "test").put("version", "1")));
    }

    public static class MyTools {
        @Tool
        String echo(String message) {
            return message;
        }
    }
}
