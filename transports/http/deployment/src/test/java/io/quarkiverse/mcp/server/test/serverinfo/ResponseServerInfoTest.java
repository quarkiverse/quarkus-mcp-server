package io.quarkiverse.mcp.server.test.serverinfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ResponseServerInfoTest extends McpServerTest {

    private static final String NAME = "TestServer";
    private static final String VERSION = "2.0";
    private static final String DESCRIPTION = "A test MCP server";

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.server-info.name", NAME)
            .overrideConfigKey("quarkus.mcp.server.server-info.version", VERSION)
            .overrideConfigKey("quarkus.mcp.server.server-info.description", DESCRIPTION);

    @Test
    public void testLightResponseServerInfo() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                // Verify tools/list response
                .message(client.newRequest(McpAssured.TOOLS_LIST))
                .withAssert(response -> {
                    JsonObject result = response.getJsonObject("result");
                    assertNotNull(result);
                    assertEquals("complete", result.getString("resultType"));
                    JsonObject meta = result.getJsonObject("_meta");
                    assertNotNull(meta, "_meta must be present in the result");
                    JsonObject serverInfo = meta.getJsonObject(MetaKey.SERVER_INFO.toString());
                    assertNotNull(serverInfo, "serverInfo must be present in _meta");
                    assertEquals(NAME, serverInfo.getString("name"));
                    assertEquals(VERSION, serverInfo.getString("version"));
                    // LIGHT mode must not include title, description, websiteUrl, icons
                    assertNull(serverInfo.getString("title"), "LIGHT mode must not include title");
                    assertNull(serverInfo.getString("description"), "LIGHT mode must not include description");
                    assertNull(serverInfo.getString("websiteUrl"), "LIGHT mode must not include websiteUrl");
                    assertNull(serverInfo.getJsonArray("icons"), "LIGHT mode must not include icons");
                })
                .send()
                // Also verify tools/call includes the same fields
                .toolsCall("alpha")
                .withRawAssert(response -> {
                    JsonObject result = response.getJsonObject("result");
                    assertNotNull(result);
                    assertEquals("complete", result.getString("resultType"));
                    JsonObject meta = result.getJsonObject("_meta");
                    assertNotNull(meta, "_meta must be present in tools/call result");
                    JsonObject serverInfo = meta.getJsonObject(MetaKey.SERVER_INFO.toString());
                    assertNotNull(serverInfo, "serverInfo must be present in _meta");
                    assertEquals(NAME, serverInfo.getString("name"));
                    assertEquals(VERSION, serverInfo.getString("version"));
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testNoResultTypeForEmptyResults() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                // Verify ping response does not include resultType
                .message(client.newRequest(McpAssured.PING))
                .withAssert(response -> {
                    JsonObject result = response.getJsonObject("result");
                    assertNotNull(result);
                    assertFalse(result.containsKey("resultType"),
                            "ping result must not contain resultType");
                })
                .send()
                // Verify logging/setLevel response does not include resultType
                .message(client.newRequest(McpMethod.LOGGING_SET_LEVEL)
                        .put("params", new JsonObject().put("level", LogLevel.INFO.toString().toLowerCase())))
                .withAssert(response -> {
                    JsonObject result = response.getJsonObject("result");
                    assertNotNull(result);
                    assertFalse(result.containsKey("resultType"),
                            "logging/setLevel result must not contain resultType");
                })
                .send()
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        String alpha() {
            return "ok";
        }
    }
}
