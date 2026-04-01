package io.quarkiverse.mcp.server.test.mcpservers;

import static io.quarkiverse.mcp.server.McpServer.DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests that the same feature name can be used on different servers.
 */
public class SameNameDifferentServersTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(AlphaFeatures.class, BravoFeatures.class))
            .overrideConfigKey("quarkus.mcp.server.support-multi-server-bindings", "false")
            .overrideConfigKey("quarkus.mcp.server.http.root-path", "/alpha/mcp")
            .overrideConfigKey("quarkus.mcp.server.bravo.http.root-path", "/bravo/mcp");

    @Test
    public void testAlpha() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setMcpPath("/alpha/mcp")
                .build()
                .connect();

        client.when()
                .toolsList(page -> {
                    assertEquals(1, page.size());
                    assertEquals("query", page.tools().get(0).name());
                })
                .toolsCall("query", r -> assertEquals("alpha_result", r.firstContent().asText().text()))
                .promptsGet("summarize", r -> assertEquals("alpha_prompt", r.messages().get(0).content().asText().text()))
                .resourcesRead("file://data", r -> assertEquals("alpha_data", r.contents().get(0).asText().text()))
                .thenAssertResults();
    }

    @Test
    public void testBravo() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setMcpPath("/bravo/mcp")
                .build()
                .connect();

        client.when()
                .toolsList(page -> {
                    assertEquals(1, page.size());
                    assertEquals("query", page.tools().get(0).name());
                })
                .toolsCall("query", r -> assertEquals("bravo_result", r.firstContent().asText().text()))
                .promptsGet("summarize", r -> assertEquals("bravo_prompt", r.messages().get(0).content().asText().text()))
                .resourcesRead("file://data", r -> assertEquals("bravo_data", r.contents().get(0).asText().text()))
                .thenAssertResults();
    }

    @McpServer(DEFAULT)
    public static class AlphaFeatures {

        @Tool
        ToolResponse query() {
            return ToolResponse.success("alpha_result");
        }

        @Prompt
        PromptMessage summarize() {
            return PromptMessage.withUserRole("alpha_prompt");
        }

        @Resource(uri = "file://data")
        TextResourceContents data(RequestUri uri) {
            return TextResourceContents.create(uri.value(), "alpha_data");
        }
    }

    @McpServer("bravo")
    public static class BravoFeatures {

        @Tool
        ToolResponse query() {
            return ToolResponse.success("bravo_result");
        }

        @Prompt
        PromptMessage summarize() {
            return PromptMessage.withUserRole("bravo_prompt");
        }

        @Resource(uri = "file://data")
        TextResourceContents data(RequestUri uri) {
            return TextResourceContents.create(uri.value(), "bravo_data");
        }
    }
}
