package io.quarkiverse.mcp.server.test.serverinfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ResponseServerInfoNoneTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.server-info.name", "NoneServer")
            .overrideConfigKey("quarkus.mcp.server.server-info.version", "1.0")
            .overrideConfigKey("quarkus.mcp.server.response-server-info", "none");

    @Test
    public void testNoneResponseServerInfo() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                // Verify tools/list response has no _meta serverInfo
                .message(client.newRequest(McpAssured.TOOLS_LIST))
                .withAssert(response -> {
                    JsonObject result = response.getJsonObject("result");
                    assertNotNull(result);
                    assertEquals("complete", result.getString("resultType"));
                    assertNull(result.getJsonObject("_meta"), "NONE mode must not include _meta");
                })
                .send()
                // Also verify tools/call has no _meta serverInfo
                .toolsCall("alpha")
                .withRawAssert(response -> {
                    JsonObject result = response.getJsonObject("result");
                    assertNotNull(result);
                    assertEquals("complete", result.getString("resultType"));
                    assertNull(result.getJsonObject("_meta"), "NONE mode must not include _meta in tools/call");
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
