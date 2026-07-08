package io.quarkiverse.mcp.server.test.mcpheader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.http.McpParamHeader;
import io.quarkiverse.mcp.server.http.runtime.StreamableHttpMcpMessageHandler;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;

public class McpParamHeaderTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(MyTools.class));

    @Inject
    ToolManager toolManager;

    @Test
    public void testInputSchemaContainsXMcpHeader() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        client.when()
                .toolsList(tools -> {
                    McpAssured.ToolInfo queryTool = tools.findByName("query");
                    assertNotNull(queryTool);
                    JsonObject inputSchema = queryTool.inputSchema();
                    JsonObject properties = inputSchema.getJsonObject("properties");
                    // region param has @McpParamHeader("Region")
                    JsonObject regionProp = properties.getJsonObject("region");
                    assertNotNull(regionProp);
                    assertEquals("Region", regionProp.getString("x-mcp-header"));
                    // value param does not have @McpParamHeader
                    JsonObject valueProp = properties.getJsonObject("value");
                    assertNotNull(valueProp);
                    assertFalse(valueProp.containsKey("x-mcp-header"));

                    // echo tool has no @McpParamHeader params
                    McpAssured.ToolInfo echoTool = tools.findByName("echo");
                    assertNotNull(echoTool);
                    JsonObject echoProp = echoTool.inputSchema().getJsonObject("properties").getJsonObject("message");
                    assertNotNull(echoProp);
                    assertFalse(echoProp.containsKey("x-mcp-header"));
                })
                .thenAssertResults();
        client.disconnect();
    }

    @Test
    public void testToolCallWithCorrectHeaders() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .setAdditionalHeaders(m -> {
                    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
                    headers.add("Mcp-Param-Region", "us-west1");
                    return headers;
                })
                .build()
                .connect();

        client.when()
                .toolsCall("query", Map.of("region", "us-west1", "value", "SELECT 1"), r -> {
                    assertFalse(r.isError());
                    assertEquals("us-west1:SELECT 1", r.firstContent().asText().text());
                })
                .thenAssertResults();
        client.disconnect();
    }

    @Test
    public void testToolCallMissingParamHeader() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        JsonObject message = newStatelessToolsCallMessage("query",
                new JsonObject().put("region", "us-west1").put("value", "SELECT 1"));

        JsonObject response = new JsonObject(RestAssured.given()
                .when()
                .headers(Map.of(
                        "Accept", "application/json, text/event-stream",
                        StreamableHttpMcpMessageHandler.MCP_PROTOCOL_VERSION_HEADER,
                        McpProtocolVersion.FIRST_STATELESS.version(),
                        StreamableHttpMcpMessageHandler.MCP_METHOD_HEADER, "tools/call",
                        StreamableHttpMcpMessageHandler.MCP_NAME_HEADER, "query"))
                .body(message.encode())
                .post(client.mcpEndpoint())
                .then()
                .statusCode(400)
                .extract().body().asString());

        JsonObject error = response.getJsonObject("error");
        assertNotNull(error);
        assertEquals(JsonRpcErrorCodes.HEADER_MISMATCH, error.getInteger("code"));
        assertTrue(error.getString("message").contains("Mcp-Param-Region"));
        client.disconnect();
    }

    @Test
    public void testToolCallMismatchedParamHeader() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        JsonObject message = newStatelessToolsCallMessage("query",
                new JsonObject().put("region", "us-west1").put("value", "SELECT 1"));

        JsonObject response = new JsonObject(RestAssured.given()
                .when()
                .headers(Map.of(
                        "Accept", "application/json, text/event-stream",
                        StreamableHttpMcpMessageHandler.MCP_PROTOCOL_VERSION_HEADER,
                        McpProtocolVersion.FIRST_STATELESS.version(),
                        StreamableHttpMcpMessageHandler.MCP_METHOD_HEADER, "tools/call",
                        StreamableHttpMcpMessageHandler.MCP_NAME_HEADER, "query",
                        StreamableHttpMcpMessageHandler.MCP_PARAM_HEADER_PREFIX + "Region", "eu-west1"))
                .body(message.encode())
                .post(client.mcpEndpoint())
                .then()
                .statusCode(400)
                .extract().body().asString());

        JsonObject error = response.getJsonObject("error");
        assertNotNull(error);
        assertEquals(JsonRpcErrorCodes.HEADER_MISMATCH, error.getInteger("code"));
        assertTrue(error.getString("message").contains("mismatch"));
        client.disconnect();
    }

    @Test
    public void testToolCallWithIntegerHeader() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .setAdditionalHeaders(m -> {
                    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
                    headers.add("Mcp-Param-Limit", "10");
                    return headers;
                })
                .build()
                .connect();

        client.when()
                .toolsCall("limitedQuery", Map.of("limit", 10, "value", "SELECT 1"), r -> {
                    assertFalse(r.isError());
                    assertEquals("10:SELECT 1", r.firstContent().asText().text());
                })
                .thenAssertResults();
        client.disconnect();
    }

    @Test
    public void testToolCallWithBooleanHeader() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .setAdditionalHeaders(m -> {
                    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
                    headers.add("Mcp-Param-DryRun", "true");
                    return headers;
                })
                .build()
                .connect();

        client.when()
                .toolsCall("dryRun", Map.of("dryRun", true, "value", "DROP TABLE"), r -> {
                    assertFalse(r.isError());
                    assertEquals("true:DROP TABLE", r.firstContent().asText().text());
                })
                .thenAssertResults();
        client.disconnect();
    }

    @Test
    public void testToolCallWithBase64EncodedHeader() {
        String value = "hello world / special=chars";
        String encoded = "=?base64?" + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)) + "?=";
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .setAdditionalHeaders(m -> {
                    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
                    headers.add("Mcp-Param-Region", encoded);
                    return headers;
                })
                .build()
                .connect();

        client.when()
                .toolsCall("query", Map.of("region", value, "value", "test"), r -> {
                    assertFalse(r.isError());
                    assertEquals(value + ":test", r.firstContent().asText().text());
                })
                .thenAssertResults();
        client.disconnect();
    }

    @Test
    public void testToolWithoutHeaderParamIgnoresValidation() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        client.when()
                .toolsCall("echo", Map.of("message", "hello"), r -> {
                    assertFalse(r.isError());
                    assertEquals("hello", r.firstContent().asText().text());
                })
                .thenAssertResults();
        client.disconnect();
    }

    @Test
    public void testProgrammaticToolSchemaContainsXMcpHeader() {
        toolManager.newTool("programmaticQuery")
                .setDescription("A programmatic tool with x-mcp-header")
                .setInputSchema(new JsonObject()
                        .put("type", "object")
                        .put("properties", new JsonObject()
                                .put("region", new JsonObject()
                                        .put("type", "string")
                                        .put("x-mcp-header", "Region"))
                                .put("value", new JsonObject()
                                        .put("type", "string")))
                        .put("required", new io.vertx.core.json.JsonArray().add("region").add("value")))
                .setHandler(args -> ToolResponse.success(args.args().get("region") + ":" + args.args().get("value")))
                .register();
        try {
            McpStreamableTestClient client = McpAssured.newStreamableClient()
                    .setStateless()
                    .build()
                    .connect();

            client.when()
                    .toolsList(tools -> {
                        McpAssured.ToolInfo tool = tools.findByName("programmaticQuery");
                        assertNotNull(tool);
                        JsonObject properties = tool.inputSchema().getJsonObject("properties");
                        assertEquals("Region", properties.getJsonObject("region").getString("x-mcp-header"));
                        assertFalse(properties.getJsonObject("value").containsKey("x-mcp-header"));
                    })
                    .thenAssertResults();
            client.disconnect();
        } finally {
            toolManager.removeTool("programmaticQuery");
        }
    }

    @Test
    public void testProgrammaticToolCallWithCorrectHeaders() {
        toolManager.newTool("programmaticQuery2")
                .setDescription("A programmatic tool with x-mcp-header")
                .setInputSchema(new JsonObject()
                        .put("type", "object")
                        .put("properties", new JsonObject()
                                .put("region", new JsonObject()
                                        .put("type", "string")
                                        .put("x-mcp-header", "Region"))
                                .put("value", new JsonObject()
                                        .put("type", "string"))))
                .setHandler(args -> ToolResponse.success(args.args().get("region") + ":" + args.args().get("value")))
                .register();
        try {
            McpStreamableTestClient client = McpAssured.newStreamableClient()
                    .setStateless()
                    .setAdditionalHeaders(m -> {
                        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
                        headers.add("Mcp-Param-Region", "us-east1");
                        return headers;
                    })
                    .build()
                    .connect();

            client.when()
                    .toolsCall("programmaticQuery2", Map.of("region", "us-east1", "value", "test"), r -> {
                        assertFalse(r.isError());
                        assertEquals("us-east1:test", r.firstContent().asText().text());
                    })
                    .thenAssertResults();
            client.disconnect();
        } finally {
            toolManager.removeTool("programmaticQuery2");
        }
    }

    @Test
    public void testProgrammaticToolCallMissingParamHeader() {
        toolManager.newTool("programmaticQuery3")
                .setDescription("A programmatic tool with x-mcp-header")
                .setInputSchema(new JsonObject()
                        .put("type", "object")
                        .put("properties", new JsonObject()
                                .put("region", new JsonObject()
                                        .put("type", "string")
                                        .put("x-mcp-header", "Region"))
                                .put("value", new JsonObject()
                                        .put("type", "string"))))
                .setHandler(args -> ToolResponse.success("unreachable"))
                .register();
        try {
            McpStreamableTestClient client = McpAssured.newStreamableClient()
                    .setStateless()
                    .build()
                    .connect();

            JsonObject message = newStatelessToolsCallMessage("programmaticQuery3",
                    new JsonObject().put("region", "us-east1").put("value", "test"));

            JsonObject response = new JsonObject(RestAssured.given()
                    .when()
                    .headers(Map.of(
                            "Accept", "application/json, text/event-stream",
                            StreamableHttpMcpMessageHandler.MCP_PROTOCOL_VERSION_HEADER,
                            McpProtocolVersion.FIRST_STATELESS.version(),
                            StreamableHttpMcpMessageHandler.MCP_METHOD_HEADER, "tools/call",
                            StreamableHttpMcpMessageHandler.MCP_NAME_HEADER, "programmaticQuery3"))
                    .body(message.encode())
                    .post(client.mcpEndpoint())
                    .then()
                    .statusCode(400)
                    .extract().body().asString());

            JsonObject error = response.getJsonObject("error");
            assertNotNull(error);
            assertEquals(JsonRpcErrorCodes.HEADER_MISMATCH, error.getInteger("code"));
            assertTrue(error.getString("message").contains("Mcp-Param-Region"));
            client.disconnect();
        } finally {
            toolManager.removeTool("programmaticQuery3");
        }
    }

    private static JsonObject newStatelessToolsCallMessage(String toolName, JsonObject arguments) {
        JsonObject meta = new JsonObject()
                .put("io.modelcontextprotocol/protocolVersion", McpProtocolVersion.FIRST_STATELESS.version())
                .put("io.modelcontextprotocol/clientInfo", new JsonObject()
                        .put("name", "test-client")
                        .put("version", "1.0"))
                .put("io.modelcontextprotocol/clientCapabilities", new JsonObject());
        return new JsonObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", "tools/call")
                .put("params", new JsonObject()
                        .put("name", toolName)
                        .put("arguments", arguments)
                        .put("_meta", meta));
    }

    public static class MyTools {

        @Tool
        String query(@McpParamHeader("Region") String region, String value) {
            return region + ":" + value;
        }

        @Tool
        String limitedQuery(@McpParamHeader("Limit") int limit, String value) {
            return limit + ":" + value;
        }

        @Tool
        String dryRun(@McpParamHeader("DryRun") boolean dryRun, String value) {
            return dryRun + ":" + value;
        }

        @Tool
        String echo(String message) {
            return message;
        }
    }

}
