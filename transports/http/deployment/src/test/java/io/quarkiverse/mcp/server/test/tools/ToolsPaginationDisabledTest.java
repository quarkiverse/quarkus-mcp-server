package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ToolsPaginationDisabledTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.mcp.server.tools.page-size", "0");

    @Inject
    ToolManager manager;

    @Test
    public void testTools() {
        int loop = 60;
        for (int i = 1; i <= loop; i++) {
            String name = i + "";
            addTool(name);
        }

        McpSseTestClient client = McpAssured.newConnectedSseClient();

        client.when()
                .toolsList(page -> {
                    assertNull(page.nextCursor());
                    assertEquals(loop, page.size());
                })
                .thenAssertResults();
    }

    private void addTool(String name) {
        manager.newTool(name)
                .setDescription(name)
                .setHandler(
                        args -> ToolResponse.success("Result " + name))
                .register();
    }

}
