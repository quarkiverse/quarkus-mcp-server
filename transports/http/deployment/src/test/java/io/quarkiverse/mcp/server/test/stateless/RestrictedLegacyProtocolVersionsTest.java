package io.quarkiverse.mcp.server.test.stateless;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.json.JsonObject;

class RestrictedLegacyProtocolVersionsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.supported-protocol-versions", "2026-07-28,2025-11-25");

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
