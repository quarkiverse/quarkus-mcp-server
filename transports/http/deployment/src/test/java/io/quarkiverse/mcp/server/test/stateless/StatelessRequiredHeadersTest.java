package io.quarkiverse.mcp.server.test.stateless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.http.runtime.StreamableHttpMcpMessageHandler;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.json.JsonObject;

public class StatelessRequiredHeadersTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(MyTools.class));

    @Test
    public void testProtocolVersionHeaderPresent() {
        JsonObject response = callHello(true);
        assertEquals("hello", response.getJsonObject("result").getJsonArray("content")
                .getJsonObject(0).getString("text"));
    }

    @Test
    public void testMissingProtocolVersionHeaderIsRejected() {
        // A stateless request (recognized via _meta) that omits the MCP-Protocol-Version header must be rejected
        // with HTTP 400 and the -32020 HeaderMismatch error (MCP spec 2026-07-28, Server Validation)
        JsonObject response = callHello(false);
        JsonObject error = response.getJsonObject("error");
        assertNotNull(error);
        assertEquals(JsonRpcErrorCodes.HEADER_MISMATCH, error.getInteger("code"));
        assertTrue(error.getString("message").contains(StreamableHttpMcpMessageHandler.MCP_PROTOCOL_VERSION_HEADER),
                error.getString("message"));
    }

    private JsonObject callHello(boolean includeProtocolVersionHeader) {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build();

        JsonObject message = client.newRequest("tools/call");
        message.put("params", new JsonObject().put("name", "hello").put("arguments", new JsonObject()));
        McpAssured.injectStatelessMeta(message);

        RequestSpecification request = RestAssured.given()
                .when()
                .header("Accept", "application/json, text/event-stream")
                .header(StreamableHttpMcpMessageHandler.MCP_METHOD_HEADER, "tools/call")
                .header(StreamableHttpMcpMessageHandler.MCP_NAME_HEADER, "hello");
        if (includeProtocolVersionHeader) {
            request.header(StreamableHttpMcpMessageHandler.MCP_PROTOCOL_VERSION_HEADER,
                    McpProtocolVersion.FIRST_STATELESS.version());
        }

        return new JsonObject(request
                .body(message.encode())
                .post(client.mcpEndpoint())
                .then()
                .statusCode(includeProtocolVersionHeader ? 200 : 400)
                .extract().body().asString());
    }

    public static class MyTools {

        @Tool
        String hello() {
            return "hello";
        }
    }
}
