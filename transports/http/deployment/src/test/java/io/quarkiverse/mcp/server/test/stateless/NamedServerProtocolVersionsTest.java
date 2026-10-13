package io.quarkiverse.mcp.server.test.stateless;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.json.JsonObject;

class NamedServerProtocolVersionsTest extends McpServerTest {
    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.supported-protocol-versions", "2026-07-28")
            .overrideConfigKey("quarkus.mcp.server.legacy.http.root-path", "/legacy/mcp")
            .overrideConfigKey("quarkus.mcp.server.unrestricted.http.root-path", "/unrestricted/mcp")
            .overrideConfigKey("quarkus.mcp.server.legacy.supported-protocol-versions", "2026-07-28,2025-11-25");

    @Test
    void policiesAreIsolatedPerServer() {
        discover("/mcp").body("result.supportedVersions", contains("2026-07-28"));
        discover("/legacy/mcp").body("result.supportedVersions", contains("2026-07-28", "2025-11-25"));
        discover("/unrestricted/mcp").body("result.supportedVersions", is(McpProtocolVersion.SUPPORTED_VERSIONS));
    }

    private static ValidatableResponse discover(String path) {
        return given().contentType("application/json").accept("application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2026-07-28").header("Mcp-Method", "server/discover")
                .body(new JsonObject().put("jsonrpc", "2.0").put("id", 1).put("method", "server/discover")
                        .put("params", new JsonObject().put("_meta", new JsonObject()
                                .put("io.modelcontextprotocol/protocolVersion", "2026-07-28")
                                .put("io.modelcontextprotocol/clientCapabilities", new JsonObject())))
                        .encode())
                .post(path).then().statusCode(200);
    }

    public static class MyTools {
        @Tool
        String echo(String message) {
            return message;
        }

        @Tool
        @McpServer("legacy")
        String legacyEcho(String message) {
            return message;
        }

        @Tool
        @McpServer("unrestricted")
        String unrestrictedEcho(String message) {
            return message;
        }
    }
}
