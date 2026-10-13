package io.quarkiverse.mcp.server.test.initrequest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.json.JsonObject;

public class UnknownProtocolVersionTest extends McpServerTest {

    static final String UNKNOWN_VERSION = "2025-09-01";

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testUnknownProtocolVersion() {
        McpStreamableTestClient client = McpAssured.newStreamableClient().build();
        // With supported-protocol-versions unset, only the versions implemented by the extension are admitted
        request().body(initialize(UNKNOWN_VERSION).encode()).post(client.mcpEndpoint()).then()
                .statusCode(400).header("Mcp-Session-Id", nullValue())
                .body("error.code", equalTo(-32022))
                .body("error.data.requested", equalTo(UNKNOWN_VERSION))
                .body("error.data.supported",
                        contains("2026-07-28", "2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05"));
    }

    @Test
    public void testUnknownProtocolVersionHeader() {
        McpStreamableTestClient client = McpAssured.newStreamableClient().build();
        request().header("MCP-Protocol-Version", UNKNOWN_VERSION)
                .body(initialize(UNKNOWN_VERSION).encode()).post(client.mcpEndpoint()).then()
                .statusCode(400).header("Mcp-Session-Id", nullValue())
                .body("error.code", equalTo(-32022))
                .body("error.data.requested", equalTo(UNKNOWN_VERSION));
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
