package io.quarkiverse.mcp.server.test.mcpservers;

import static io.quarkiverse.mcp.server.sse.runtime.StreamableHttpMcpMessageHandler.MCP_SESSION_ID_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.StreamableHttpTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class MultipleServersActiveTest extends StreamableHttpTest {

    @RegisterExtension
    static final QuarkusUnitTest config = config(500)
            .withApplicationRoot(
                    root -> root.addClasses(MyFeatures.class))
            .overrideConfigKey("quarkus.mcp.server.sse.root-path", "/alpha/mcp")
            .overrideConfigKey("quarkus.mcp.server.bravo.sse.root-path", "/bravo/mcp")
            .overrideConfigKey("quarkus.mcp.server.charlie.sse.root-path", "/charlie/mcp");

    static QuarkusUnitTest config(int textLimit) {
        QuarkusUnitTest ret = defaultConfig(500);
        if (System.getProperty("logTraffic") != null) {
            ret.overrideConfigKey("quarkus.mcp.server.bravo.traffic-logging.enabled", "true");
            ret.overrideConfigKey("quarkus.mcp.server.bravo.traffic-logging.text-limit", "" + textLimit);
            ret.overrideConfigKey("quarkus.mcp.server.charlie.traffic-logging.enabled", "true");
            ret.overrideConfigKey("quarkus.mcp.server.charlie.traffic-logging.text-limit", "" + textLimit);
        }
        return ret;
    }

    @Test
    public void testAlpha() {
        String mcpSessionId = initSession(createMessageEndpoint("/alpha/mcp"));

        JsonObject m1 = newToolCallMessage("alpha");
        assertToolTextContent(m1, new JsonObject(send(m1, Map.of(MCP_SESSION_ID_HEADER, mcpSessionId)).extract().asString()),
                "1");
        JsonObject m2 = newToolCallMessage("bravo");
        assertErrorMessage(m2, new JsonObject(
                send(m2, Map.of(MCP_SESSION_ID_HEADER, mcpSessionId)).extract().asString()),
                "Invalid tool name: bravo");
        JsonObject m3 = newToolCallMessage("charlie");
        assertErrorMessage(m3, new JsonObject(
                send(m3, Map.of(MCP_SESSION_ID_HEADER, mcpSessionId)).extract().asString()),
                "Invalid tool name: charlie");
    }

    @Test
    public void testBravo() {
        String mcpSessionId = initSession(createMessageEndpoint("/bravo/mcp"));

        JsonObject m1 = newToolCallMessage("alpha");
        assertErrorMessage(m1, new JsonObject(
                send(m1, Map.of(MCP_SESSION_ID_HEADER, mcpSessionId)).extract().asString()),
                "Invalid tool name: alpha");
        JsonObject m2 = newToolCallMessage("bravo");
        assertToolTextContent(m2, new JsonObject(send(m2, Map.of(MCP_SESSION_ID_HEADER, mcpSessionId)).extract().asString()),
                "2");
        JsonObject m3 = newToolCallMessage("charlie");
        assertErrorMessage(m3, new JsonObject(
                send(m3, Map.of(MCP_SESSION_ID_HEADER, mcpSessionId)).extract().asString()),
                "Invalid tool name: charlie");
    }

    @Test
    public void testCharlie() {
        String mcpSessionId = initSession(createMessageEndpoint("/charlie/mcp"));

        JsonObject m1 = newToolCallMessage("alpha");
        assertErrorMessage(m1, new JsonObject(
                send(m1, Map.of(MCP_SESSION_ID_HEADER, mcpSessionId)).extract().asString()),
                "Invalid tool name: alpha");
        JsonObject m2 = newToolCallMessage("bravo");
        assertErrorMessage(m2, new JsonObject(
                send(m2, Map.of(MCP_SESSION_ID_HEADER, mcpSessionId)).extract().asString()),
                "Invalid tool name: bravo");
        JsonObject m3 = newToolCallMessage("charlie");
        assertToolTextContent(m3, new JsonObject(send(m3, Map.of(MCP_SESSION_ID_HEADER, mcpSessionId)).extract().asString()),
                "3");
    }

    private void assertToolTextContent(JsonObject request, JsonObject response, String expectedText) {
        JsonObject result = assertResultResponse(request, response);
        assertNotNull(result);
        assertFalse(result.getBoolean("isError"));
        JsonArray content = result.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        String text4 = textContent.getString("text");
        assertEquals(expectedText, text4);
    }

    @McpServer("charlie")
    public static class MyFeatures {

        @McpServer(McpServer.DEFAULT)
        @Tool
        String alpha() {
            return "1";
        }

        @McpServer("bravo")
        @Tool
        String bravo() {
            return "2";
        }

        @Tool
        String charlie() {
            return "3";
        }

    }
}
