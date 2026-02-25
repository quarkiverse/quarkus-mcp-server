package io.quarkiverse.mcp.server.websocket.test.mcpservers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpWebSocketTestClient;
import io.quarkiverse.mcp.server.websocket.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class MultipleServersActiveTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = config(500)
            .withApplicationRoot(
                    root -> root.addClasses(MyFeatures.class))
            .overrideConfigKey("quarkus.mcp.server.websocket.endpoint-path", "/alpha/mcp")
            .overrideConfigKey("quarkus.mcp.server.bravo.websocket.endpoint-path", "/bravo/mcp")
            .overrideConfigKey("quarkus.mcp.server.charlie.websocket.endpoint-path", "/charlie/mcp");

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
        McpWebSocketTestClient client = McpAssured.newWebSocketClient()
                .setEndpointPath("/alpha/mcp")
                .build()
                .connect();

        client.when()
                .toolsCall("alpha", response -> {
                    assertEquals("1", response.content().get(0).asText().text());
                })
                .toolsCall("bravo")
                .withErrorAssert(error -> {
                    assertEquals("Invalid tool name: bravo", error.message());
                })
                .send()
                .toolsCall("charlie")
                .withErrorAssert(error -> {
                    assertEquals("Invalid tool name: charlie", error.message());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testBravo() {
        McpWebSocketTestClient client = McpAssured.newWebSocketClient()
                .setEndpointPath("/bravo/mcp")
                .build()
                .connect();

        client.when()
                .toolsCall("bravo", response -> {
                    assertEquals("2", response.content().get(0).asText().text());
                })
                .toolsCall("alpha")
                .withErrorAssert(error -> {
                    assertEquals("Invalid tool name: alpha", error.message());
                })
                .send()
                .toolsCall("charlie")
                .withErrorAssert(error -> {
                    assertEquals("Invalid tool name: charlie", error.message());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testCharlie() {
        McpWebSocketTestClient client = McpAssured.newWebSocketClient()
                .setEndpointPath("/charlie/mcp")
                .build()
                .connect();

        client.when()
                .toolsCall("charlie", response -> {
                    assertEquals("3", response.content().get(0).asText().text());
                })
                .toolsCall("alpha")
                .withErrorAssert(error -> {
                    assertEquals("Invalid tool name: alpha", error.message());
                })
                .send()
                .toolsCall("bravo")
                .withErrorAssert(error -> {
                    assertEquals("Invalid tool name: bravo", error.message());
                })
                .send()
                .thenAssertResults();
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
