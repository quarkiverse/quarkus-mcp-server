package io.quarkiverse.mcp.server.test.mcpservers;

import static io.quarkiverse.mcp.server.McpServer.DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

/**
 * Verifies that the automatic {@code notifications/*_list_changed} notification sent when a feature is registered or removed
 * programmatically is scoped to the connections of the affected server(s) only, which is the default
 * {@code MATCHING_SERVER} strategy.
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-mcp-server/issues/983">#983</a>
 */
public class AutoListChangedServerScopeTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(AutoListChangedServerScopeTest.class))
            .overrideConfigKey("quarkus.mcp.server.http.root-path", "/alpha/mcp")
            .overrideConfigKey("quarkus.mcp.server.bravo.http.root-path", "/bravo/mcp")
            // Enable DEBUG for the bravo server too, so the subsidiary SSE debug log notification is sent
            .overrideRuntimeConfigKey("quarkus.mcp.server.bravo.client-logging.default-level", "DEBUG");

    @Inject
    ToolManager toolManager;

    @Inject
    PromptManager promptManager;

    @Inject
    ResourceManager resourceManager;

    @Inject
    ResourceTemplateManager resourceTemplateManager;

    @Test
    public void testListChangedIsScopedToServer() {
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
        // Notification counts tracked per client (start at 1 because of the debug log notification)
        int alpha = 1;
        int bravo = 1;

        // --- Tools ---

        // Register a tool on bravo only -> only the bravo client is notified
        toolManager.newTool("t-bravo")
                .setServerName("bravo")
                .setDescription("bravo tool")
                .setHandler(ta -> ToolResponse.success("ok"))
                .register();
        assertLastNotification(bravoClient, ++bravo, "notifications/tools/list_changed");
        assertEquals(alpha, alphaClient.snapshot().notifications().size());

        // Register a tool on the default (alpha) server -> only the alpha client is notified
        toolManager.newTool("t-alpha")
                .setServerName(DEFAULT)
                .setDescription("alpha tool")
                .setHandler(ta -> ToolResponse.success("ok"))
                .register();
        assertLastNotification(alphaClient, ++alpha, "notifications/tools/list_changed");
        assertEquals(bravo, bravoClient.snapshot().notifications().size());

        // Remove the alpha tool -> only the alpha client is notified
        assertNotNull(toolManager.removeTool("t-alpha", DEFAULT));
        assertLastNotification(alphaClient, ++alpha, "notifications/tools/list_changed");
        assertEquals(bravo, bravoClient.snapshot().notifications().size());

        // Remove the bravo tool -> only the bravo client is notified
        assertNotNull(toolManager.removeTool("t-bravo", "bravo"));
        assertLastNotification(bravoClient, ++bravo, "notifications/tools/list_changed");
        assertEquals(alpha, alphaClient.snapshot().notifications().size());

        // --- Prompts ---

        promptManager.newPrompt("p-bravo")
                .setServerName("bravo")
                .setDescription("bravo prompt")
                .setHandler(pa -> PromptResponse.withMessages(PromptMessage.withUserRole("ok")))
                .register();
        assertLastNotification(bravoClient, ++bravo, "notifications/prompts/list_changed");
        assertEquals(alpha, alphaClient.snapshot().notifications().size());

        assertNotNull(promptManager.removePrompt("p-bravo", "bravo"));
        assertLastNotification(bravoClient, ++bravo, "notifications/prompts/list_changed");
        assertEquals(alpha, alphaClient.snapshot().notifications().size());

        // --- Resources ---

        resourceManager.newResource("r-bravo")
                .setServerName("bravo")
                .setUri("file://r-bravo")
                .setDescription("bravo resource")
                .setHandler(ra -> new ResourceResponse(
                        List.of(TextResourceContents.create(ra.requestUri().value(), "ok"))))
                .register();
        assertLastNotification(bravoClient, ++bravo, "notifications/resources/list_changed");
        assertEquals(alpha, alphaClient.snapshot().notifications().size());

        assertNotNull(resourceManager.removeResource("file://r-bravo", "bravo"));
        assertLastNotification(bravoClient, ++bravo, "notifications/resources/list_changed");
        assertEquals(alpha, alphaClient.snapshot().notifications().size());

        // --- Resource templates ---

        resourceTemplateManager.newResourceTemplate("rt-bravo")
                .setServerName("bravo")
                .setUriTemplate("file://rt-bravo/{foo}")
                .setDescription("bravo resource template")
                .setHandler(args -> null)
                .register();
        assertLastNotification(bravoClient, ++bravo, "notifications/resources/list_changed");
        assertEquals(alpha, alphaClient.snapshot().notifications().size());

        assertNotNull(resourceTemplateManager.removeResourceTemplate("rt-bravo", "bravo"));
        assertLastNotification(bravoClient, ++bravo, "notifications/resources/list_changed");
        assertEquals(alpha, alphaClient.snapshot().notifications().size());

        alphaClient.disconnect();
        bravoClient.disconnect();
    }

    private static void assertLastNotification(McpStreamableTestClient client, int expectedCount, String expectedMethod) {
        List<JsonObject> notifications = client.waitForNotifications(expectedCount).notifications();
        assertEquals(expectedCount, notifications.size());
        assertEquals(expectedMethod, notifications.get(expectedCount - 1).getString("method"));
    }
}
