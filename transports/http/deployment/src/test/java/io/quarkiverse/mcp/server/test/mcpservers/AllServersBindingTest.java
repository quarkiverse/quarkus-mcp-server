package io.quarkiverse.mcp.server.test.mcpservers;

import static io.quarkiverse.mcp.server.McpServer.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.runtime.Startup;
import io.quarkus.test.QuarkusUnitTest;

public class AllServersBindingTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyFeatures.class))
            .overrideConfigKey("quarkus.mcp.server.http.root-path", "/alpha/mcp")
            .overrideConfigKey("quarkus.mcp.server.bravo.http.root-path", "/bravo/mcp")
            .overrideConfigKey("quarkus.mcp.server.charlie.http.root-path", "/charlie/mcp");

    @Inject
    ToolManager toolManager;

    @Test
    public void testServerNames() {
        // @McpServer(McpServer.ALL) is expanded to all known server names
        for (String server : new String[] { DEFAULT, "bravo", "charlie" }) {
            ToolManager.ToolInfo all = toolManager.getTool("all", server);
            assertNotNull(all, "Tool 'all' should be bound to server: " + server);
            assertThat(all.serverNames()).containsExactlyInAnyOrder(DEFAULT, "bravo", "charlie");

            // Programmatic registration with setServerName(McpServer.ALL)
            ToolManager.ToolInfo echo = toolManager.getTool("echo", server);
            assertNotNull(echo, "Tool 'echo' should be bound to server: " + server);
            assertThat(echo.serverNames()).containsExactlyInAnyOrder(DEFAULT, "bravo", "charlie");
        }
    }

    @Test
    public void testAllServers() {
        for (String path : new String[] { "/alpha/mcp", "/bravo/mcp", "/charlie/mcp" }) {
            McpStreamableTestClient client = McpAssured.newStreamableClient()
                    .setMcpPath(path)
                    .build()
                    .connect();
            client.when()
                    .toolsCall("all", r -> assertEquals("all", r.firstContent().asText().text()))
                    .toolsCall("echo", r -> assertEquals("echo", r.firstContent().asText().text()))
                    .thenAssertResults();
        }
    }

    public static class MyFeatures {

        @Inject
        ToolManager toolManager;

        @Startup
        void start() {
            toolManager.newTool("echo")
                    .setServerName(McpServer.ALL)
                    .setHandler(ta -> ToolResponse.success("echo"))
                    .setDescription("echo")
                    .register();
        }

        @Tool
        @McpServer(McpServer.ALL)
        String all() {
            return "all";
        }

    }

}
