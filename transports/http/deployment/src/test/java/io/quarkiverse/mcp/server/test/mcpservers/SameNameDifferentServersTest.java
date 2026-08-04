package io.quarkiverse.mcp.server.test.mcpservers;

import static io.quarkiverse.mcp.server.McpServer.DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

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
import io.vertx.core.json.JsonObject;

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
                    assertEquals(2, page.size());
                    assertEquals("query", page.tools().get(1).name());

                    // Verify the schema uses AlphaItem (has "label" property)
                    JsonObject schema = page.findByName("analyze").inputSchema();
                    JsonObject properties = schema.getJsonObject("properties");
                    assertNotNull(properties);
                    JsonObject itemProps = properties.getJsonObject("item").getJsonObject("properties");
                    assertNotNull(itemProps.getJsonObject("label"),
                            "Alpha's analyze tool should use AlphaItem with 'label' property");
                })
                .toolsCall("query", r -> assertEquals("alpha_result", r.firstContent().asText().text()))
                .toolsCall("analyze", Map.of("item", new AlphaItem("test")),
                        r -> assertEquals("alpha:test", r.firstContent().asText().text()))
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
                    assertEquals(2, page.size());
                    assertEquals("query", page.tools().get(1).name());

                    // Verify the schema uses BravoItem (has "code" property)
                    JsonObject schema = page.findByName("analyze").inputSchema();
                    JsonObject properties = schema.getJsonObject("properties");
                    assertNotNull(properties);
                    JsonObject itemProps = properties.getJsonObject("item").getJsonObject("properties");
                    assertNotNull(itemProps.getJsonObject("code"),
                            "Bravo's analyze tool should use BravoItem with 'code' property");
                })
                .toolsCall("query", r -> assertEquals("bravo_result", r.firstContent().asText().text()))
                .toolsCall("analyze", Map.of("item", new BravoItem(42)),
                        r -> assertEquals("bravo:42", r.firstContent().asText().text()))
                .promptsGet("summarize", r -> assertEquals("bravo_prompt", r.messages().get(0).content().asText().text()))
                .resourcesRead("file://data", r -> assertEquals("bravo_data", r.contents().get(0).asText().text()))
                .thenAssertResults();
    }

    public record AlphaItem(String label) {
    }

    public record BravoItem(int code) {
    }

    @McpServer(DEFAULT)
    public static class AlphaFeatures {

        @Tool
        ToolResponse query() {
            return ToolResponse.success("alpha_result");
        }

        @Tool
        String analyze(AlphaItem item) {
            return "alpha:" + item.label();
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

        @Tool
        String analyze(BravoItem item) {
            return "bravo:" + item.code();
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
