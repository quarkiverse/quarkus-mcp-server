package io.quarkiverse.mcp.server.test.stateless;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.json.JsonObject;

class RestrictedProtocolVersionsTest extends McpServerTest {

    private static final String VERSION = "2026-07-28";

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.supported-protocol-versions", VERSION)
            // A restricted version set must take precedence over legacy auto-initialization.
            .overrideConfigKey("quarkus.mcp.server.http.streamable.auto-init", "true");

    @Test
    void discoveryAndAdmissionUseTheConfiguredVersions() {
        post("server/discover", VERSION)
                .statusCode(200)
                .header("Mcp-Session-Id", nullValue())
                .body("result.supportedVersions", contains(VERSION));
        post("tools/list", VERSION)
                .statusCode(200)
                .header("Mcp-Session-Id", nullValue())
                .body("result.tools[0].name", equalTo("echo"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25", "2099-01-01" })
    void rejectsVersionsOutsideTheConfiguredSet(String version) {
        post("tools/list", version)
                .statusCode(400)
                .header("Mcp-Session-Id", nullValue())
                .body("error.code", equalTo(-32022))
                .body("error.data.supported", contains(VERSION))
                .body("error.data.requested", equalTo(version));
    }

    @Test
    void missingHeaderCannotFallBackToLegacyProtocol() {
        request("tools/list")
                .body(message("tools/list", VERSION).encode())
                .post("/mcp")
                .then()
                .statusCode(400)
                .body("error.code", equalTo(-32020));
    }

    @Test
    void missingHeaderCannotStartALegacySession() {
        request("initialize")
                .body(new JsonObject().put("jsonrpc", "2.0").put("id", 1).put("method", "initialize")
                        .put("params", new JsonObject().put("protocolVersion", "2025-11-25")
                                .put("capabilities", new JsonObject())
                                .put("clientInfo", new JsonObject().put("name", "test").put("version", "1")))
                        .encode())
                .post("/mcp")
                .then()
                .statusCode(400)
                .header("Mcp-Session-Id", nullValue())
                .body("error.code", equalTo(-32020));
    }

    @ParameterizedTest
    @ValueSource(strings = { "GET", "DELETE" })
    void legacySessionOperationsCannotBypassTheVersionPolicy(String method) {
        request("tools/list").request(method, "/mcp").then()
                .statusCode(405)
                .header("Allow", equalTo("POST"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/mcp/sse", "/mcp/messages/old-session" })
    void legacyTransportCannotBypassTheVersionPolicy(String path) {
        request("initialize").body(message("initialize", "2024-11-05").encode())
                .post(path).then().statusCode(404);
    }

    @Test
    void legacySseConnectionCannotBypassTheVersionPolicy() {
        given().config(io.restassured.config.RestAssuredConfig.config()
                .httpClient(io.restassured.config.HttpClientConfig.httpClientConfig()
                        .setParam("http.socket.timeout", 5000)))
                .accept("text/event-stream").get("/mcp/sse").then().statusCode(404);
    }

    @Test
    void initializeCannotCreateASessionWithAModernHeader() {
        post("initialize", VERSION).statusCode(404)
                .header("Mcp-Session-Id", nullValue())
                .body("error.code", equalTo(-32601));
    }

    @Test
    void preservesHeaderValidation() {
        request("tools/list")
                .header("MCP-Protocol-Version", VERSION)
                .body(message("server/discover", VERSION).encode())
                .post("/mcp").then()
                .statusCode(400).body("error.code", equalTo(-32020));
    }

    @Test
    void preservesDiscoveryIdentity() {
        post("server/discover", VERSION).statusCode(200)
                .body("result._meta.'io.modelcontextprotocol/serverInfo'.name", notNullValue());
    }

    private static ValidatableResponse post(String method, String version) {
        return request(method).header("MCP-Protocol-Version", version)
                .body(message(method, version).encode()).post("/mcp").then();
    }

    private static RequestSpecification request(String method) {
        return given().contentType("application/json").accept("application/json, text/event-stream")
                .header("Mcp-Method", method);
    }

    private static JsonObject message(String method, String version) {
        return new JsonObject().put("jsonrpc", "2.0").put("id", 1).put("method", method)
                .put("params", new JsonObject().put("_meta", new JsonObject()
                        .put("io.modelcontextprotocol/protocolVersion", version)
                        .put("io.modelcontextprotocol/clientCapabilities", new JsonObject())));
    }

    public static class MyTools {
        @Tool
        String echo(String message) {
            return message;
        }
    }
}
