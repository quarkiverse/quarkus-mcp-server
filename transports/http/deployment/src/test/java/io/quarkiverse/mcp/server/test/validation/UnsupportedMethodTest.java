package io.quarkiverse.mcp.server.test.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.runtime.Messages;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;

public class UnsupportedMethodTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication();

    @Test
    public void testError() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .message(Messages.newRequest(100, "alpha/bravo"))
                .withErrorAssert(e -> {
                    assertEquals(JsonRpcErrorCodes.METHOD_NOT_FOUND, e.code());
                    assertEquals("Unsupported method: alpha/bravo", e.message());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testErrorStreamable() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .message(Messages.newRequest(100, "alpha/bravo"))
                .withErrorAssert(e -> {
                    assertEquals(JsonRpcErrorCodes.METHOD_NOT_FOUND, e.code());
                    assertEquals("Unsupported method: alpha/bravo", e.message());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testErrorStateless() {
        // The stateless Streamable HTTP transport must respond with HTTP 404 Not Found for an unknown
        // method, in addition to the -32601 JSON-RPC error body (MCP spec 2026-07-28). A raw HTTP call is
        // used here because the MCP test client does not expose the HTTP status code
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build();

        JsonObject message = client.newRequest("alpha/bravo");
        McpAssured.injectStatelessMeta(message);

        JsonObject response = new JsonObject(RestAssured.given()
                .when()
                .header("Accept", "application/json, text/event-stream")
                .header("Mcp-Method", "alpha/bravo")
                .header("Mcp-Protocol-Version", McpProtocolVersion.FIRST_STATELESS.version())
                .body(message.encode())
                .post(client.mcpEndpoint())
                .then()
                .statusCode(404)
                .extract().body().asString());

        JsonObject error = response.getJsonObject("error");
        assertNotNull(error, "An unknown method must produce a JSON-RPC error");
        assertEquals(JsonRpcErrorCodes.METHOD_NOT_FOUND, error.getInteger("code"));
        assertEquals("Unsupported method: alpha/bravo", error.getString("message"));
    }

}
