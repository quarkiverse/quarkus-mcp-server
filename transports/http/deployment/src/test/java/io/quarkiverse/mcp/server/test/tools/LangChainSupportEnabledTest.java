package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class LangChainSupportEnabledTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.support-langchain4j-annotations", "true");

    @Test
    public void testSupport() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsList(page -> {
                    assertEquals(2, page.size());
                    assertEquals("charlie", page.findByName("charlie").name());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        TextContent bravo(int price) {
            throw new ToolCallException("Business error");
        }

        @dev.langchain4j.agent.tool.Tool
        TextContent charlie(int price) {
            throw new ToolCallException("Business error");
        }

    }

}
