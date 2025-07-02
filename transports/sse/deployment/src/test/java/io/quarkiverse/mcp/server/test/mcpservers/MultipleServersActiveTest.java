package io.quarkiverse.mcp.server.test.mcpservers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class MultipleServersActiveTest extends McpServerTest {

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
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setMcpPath("/alpha/mcp")
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
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setMcpPath("/bravo/mcp")
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
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setMcpPath("/charlie/mcp")
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
