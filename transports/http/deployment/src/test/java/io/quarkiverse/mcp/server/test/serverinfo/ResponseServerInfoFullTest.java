package io.quarkiverse.mcp.server.test.serverinfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ResponseServerInfoFullTest extends McpServerTest {

    private static final String NAME = "FullServer";
    private static final String VERSION = "3.0";
    private static final String DESCRIPTION = "A full MCP server";
    private static final String URL = "https://example.com";
    private static final String ICON_URL = "https://example.com/icon.png";

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.server-info.name", NAME)
            .overrideConfigKey("quarkus.mcp.server.server-info.version", VERSION)
            .overrideConfigKey("quarkus.mcp.server.server-info.description", DESCRIPTION)
            .overrideConfigKey("quarkus.mcp.server.server-info.website-url", URL)
            .overrideConfigKey("quarkus.mcp.server.server-info.icons[0].src", ICON_URL)
            .overrideConfigKey("quarkus.mcp.server.server-info.icons[0].mime-type", "image/png")
            .overrideConfigKey("quarkus.mcp.server.server-info.icons[0].theme", "dark")
            .overrideConfigKey("quarkus.mcp.server.server-info.icons[0].sizes[0]", "48x48")
            .overrideConfigKey("quarkus.mcp.server.response-server-info", "full");

    @Test
    public void testFullResponseServerInfo() {
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
                    assertEquals(NAME, serverInfo.getString("title"));
                    assertEquals(DESCRIPTION, serverInfo.getString("description"));
                    assertEquals(URL, serverInfo.getString("websiteUrl"));
                    JsonArray icons = serverInfo.getJsonArray("icons");
                    assertNotNull(icons, "FULL mode must include icons");
                    assertEquals(1, icons.size());
                    JsonObject icon = icons.getJsonObject(0);
                    assertEquals(ICON_URL, icon.getString("src"));
                    assertEquals("image/png", icon.getString("mimeType"));
                    assertEquals("dark", icon.getString("theme"));
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
                    assertEquals(NAME, serverInfo.getString("title"));
                    assertEquals(DESCRIPTION, serverInfo.getString("description"));
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
