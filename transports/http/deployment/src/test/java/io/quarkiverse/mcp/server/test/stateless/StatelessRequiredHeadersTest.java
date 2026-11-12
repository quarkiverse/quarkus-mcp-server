package io.quarkiverse.mcp.server.test.stateless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.http.runtime.StreamableHttpMcpMessageHandler;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class StatelessRequiredHeadersTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(MyTools.class));

    @Test
    public void testAllHeadersPresent() {
        McpStreamableTestClient client = newClient();
        JsonObject message = newHelloMessage(client);
        JsonObject response = post(client, message, allHeaders(), 200);
        assertEquals("hello", response.getJsonObject("result").getJsonArray("content")
                .getJsonObject(0).getString("text"));
    }

    @Test
    public void testMissingProtocolVersionHeaderIsRejected() {
        // A stateless request (recognized via _meta) that omits the MCP-Protocol-Version header must be rejected
        // with HTTP 400 and the -32020 HeaderMismatch error (MCP spec 2026-07-28, Server Validation)
        assertRejectedWithHeaderMismatch(
                headers -> headers.remove(StreamableHttpMcpMessageHandler.MCP_PROTOCOL_VERSION_HEADER),
                StreamableHttpMcpMessageHandler.MCP_PROTOCOL_VERSION_HEADER);
    }

    @Test
    public void testProtocolVersionHeaderMismatchIsRejected() {
        assertRejectedWithHeaderMismatch(
                headers -> headers.put(StreamableHttpMcpMessageHandler.MCP_PROTOCOL_VERSION_HEADER,
                        McpProtocolVersion.V_2025_06_18.version()),
                StreamableHttpMcpMessageHandler.MCP_PROTOCOL_VERSION_HEADER);
    }

    @Test
    public void testMissingMethodHeaderIsRejected() {
        assertRejectedWithHeaderMismatch(
                headers -> headers.remove(StreamableHttpMcpMessageHandler.MCP_METHOD_HEADER),
                StreamableHttpMcpMessageHandler.MCP_METHOD_HEADER);
    }

    @Test
    public void testMismatchedMethodHeaderIsRejected() {
        assertRejectedWithHeaderMismatch(
                headers -> headers.put(StreamableHttpMcpMessageHandler.MCP_METHOD_HEADER, "prompts/list"),
                StreamableHttpMcpMessageHandler.MCP_METHOD_HEADER);
    }

    @Test
    public void testMissingNameHeaderIsRejected() {
        assertRejectedWithHeaderMismatch(
                headers -> headers.remove(StreamableHttpMcpMessageHandler.MCP_NAME_HEADER),
                StreamableHttpMcpMessageHandler.MCP_NAME_HEADER);
    }

    @Test
    public void testMismatchedNameHeaderIsRejected() {
        assertRejectedWithHeaderMismatch(
                headers -> headers.put(StreamableHttpMcpMessageHandler.MCP_NAME_HEADER, "nonexistent"),
                StreamableHttpMcpMessageHandler.MCP_NAME_HEADER);
    }

    @Test
    public void testMalformedIdIsNotEchoed() {
        // The validation errors are returned before the message is validated as JSON-RPC
        // A malformed id (not a string or number) could not be read and must not be echoed back
        for (Object malformedId : new Object[] { new JsonObject().put("foo", 1), new JsonArray().add(1), true }) {
            McpStreamableTestClient client = newClient();
            JsonObject message = newHelloMessage(client);
            message.put("id", malformedId);
            Map<String, String> headers = allHeaders();
            headers.remove(StreamableHttpMcpMessageHandler.MCP_METHOD_HEADER);
            JsonObject response = post(client, message, headers, 400);

            JsonObject error = response.getJsonObject("error");
            assertNotNull(error);
            assertEquals(JsonRpcErrorCodes.HEADER_MISMATCH, error.getInteger("code"));
            assertTrue(response.containsKey("id"), response.encode());
            assertNull(response.getValue("id"), response.encode());
        }
    }

    private static McpStreamableTestClient newClient() {
        return McpAssured.newStreamableClient()
                .setStateless()
                .build();
    }

    private static Map<String, String> allHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put(StreamableHttpMcpMessageHandler.MCP_PROTOCOL_VERSION_HEADER,
                McpProtocolVersion.FIRST_STATELESS.version());
        headers.put(StreamableHttpMcpMessageHandler.MCP_METHOD_HEADER, "tools/call");
        headers.put(StreamableHttpMcpMessageHandler.MCP_NAME_HEADER, "hello");
        return headers;
    }

    private static JsonObject newHelloMessage(McpStreamableTestClient client) {
        JsonObject message = client.newRequest("tools/call");
        message.put("params", new JsonObject().put("name", "hello").put("arguments", new JsonObject()));
        McpAssured.injectStatelessMeta(message);
        return message;
    }

    private static JsonObject post(McpStreamableTestClient client, JsonObject message, Map<String, String> headers,
            int expectedStatus) {
        RequestSpecification request = RestAssured.given()
                .when()
                .header("Accept", "application/json, text/event-stream");
        headers.forEach(request::header);

        return new JsonObject(request
                .body(message.encode())
                .post(client.mcpEndpoint())
                .then()
                .statusCode(expectedStatus)
                .extract().body().asString());
    }

    private void assertRejectedWithHeaderMismatch(Consumer<Map<String, String>> headersConsumer, String headerName) {
        McpStreamableTestClient client = newClient();
        JsonObject message = newHelloMessage(client);
        Map<String, String> headers = allHeaders();
        headersConsumer.accept(headers);
        JsonObject response = post(client, message, headers, 400);

        JsonObject error = response.getJsonObject("error");
        assertNotNull(error);
        assertEquals(JsonRpcErrorCodes.HEADER_MISMATCH, error.getInteger("code"));
        assertTrue(error.getString("message").contains(headerName),
                error.getString("message"));
        // Error responses must include the same id as the request they respond to,
        // unless the id could not be read (MCP spec, Error Responses)
        assertEquals(message.getValue("id"), response.getValue("id"), response.encode());
    }

    public static class MyTools {

        @Tool
        String hello() {
            return "hello";
        }
    }
}
