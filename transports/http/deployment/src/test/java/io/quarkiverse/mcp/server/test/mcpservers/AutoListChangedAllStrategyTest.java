package io.quarkiverse.mcp.server.test.mcpservers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

/**
 * Verifies that {@code quarkus.mcp.server.auto-list-changed-strategy=all} restores the legacy behavior where the automatic
 * {@code notifications/*_list_changed} notification is broadcast to all connections, regardless of the server they are
 * connected to.
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-mcp-server/issues/983">#983</a>
 */
public class AutoListChangedAllStrategyTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(AutoListChangedAllStrategyTest.class))
            .overrideConfigKey("quarkus.mcp.server.auto-list-changed-strategy", "all")
            .overrideConfigKey("quarkus.mcp.server.http.root-path", "/alpha/mcp")
            .overrideConfigKey("quarkus.mcp.server.bravo.http.root-path", "/bravo/mcp")
            // Enable DEBUG for the bravo server too, so the subsidiary SSE debug log notification is sent
            .overrideRuntimeConfigKey("quarkus.mcp.server.bravo.client-logging.default-level", "DEBUG");

    @Inject
    ToolManager toolManager;

    @Test
    public void testListChangedIsBroadcastToAllServers() {
        McpStreamableTestClient alphaClient = McpAssured.newStreamableClient()
                .setMcpPath("/alpha/mcp")
                .setOpenSubsidiarySse(true)
                .build()
                .connect();
        McpStreamableTestClient bravoClient = McpAssured.newStreamableClient()
                .setMcpPath("/bravo/mcp")
                .setOpenSubsidiarySse(true)
                .build()
                .connect();

        // Wait for the subsidiary SSE debug log notification on both clients
        alphaClient.waitForNotifications(1);
        bravoClient.waitForNotifications(1);

        // Register a tool on bravo only -> both clients are notified because the strategy is ALL
        toolManager.newTool("t-bravo")
                .setServerName("bravo")
                .setDescription("bravo tool")
                .setHandler(ta -> ToolResponse.success("ok"))
                .register();
        assertLastNotification(bravoClient, 2, "notifications/tools/list_changed");
        assertLastNotification(alphaClient, 2, "notifications/tools/list_changed");

        // Remove the tool from bravo -> both clients are notified as well
        assertNotNull(toolManager.removeTool("t-bravo", "bravo"));
        assertLastNotification(bravoClient, 3, "notifications/tools/list_changed");
        assertLastNotification(alphaClient, 3, "notifications/tools/list_changed");

        alphaClient.disconnect();
        bravoClient.disconnect();
    }

    private static void assertLastNotification(McpStreamableTestClient client, int expectedCount, String expectedMethod) {
        List<JsonObject> notifications = client.waitForNotifications(expectedCount).notifications();
        assertEquals(expectedCount, notifications.size());
        assertEquals(expectedMethod, notifications.get(expectedCount - 1).getString("method"));
    }
}
