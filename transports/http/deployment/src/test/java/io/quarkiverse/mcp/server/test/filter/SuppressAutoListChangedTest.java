package io.quarkiverse.mcp.server.test.filter;

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
 * Verifies that {@code setNotifyListChanged(false)} suppresses the automatic {@code list_changed} notification both when a
 * feature (tool, prompt, resource or resource template) is registered and when it is later removed, while the caller can still
 * trigger notifications explicitly.
 * <p>
 * Each suppressed operation is followed by a normally-registered feature of a <em>different</em> type. Because notifications
 * are delivered in order on the stream, if a suppressed operation had (wrongly) sent its notification, it would arrive before
 * the control notification and be detected by the method assertion.
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-mcp-server/issues/983">#983</a>
 */
public class SuppressAutoListChangedTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig().withEmptyApplication();

    @Inject
    ToolManager toolManager;

    @Inject
    PromptManager promptManager;

    @Inject
    ResourceManager resourceManager;

    @Inject
    ResourceTemplateManager resourceTemplateManager;

    @Test
    public void testSuppressedNotifications() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setOpenSubsidiarySse(true)
                .build()
                .connect();

        // Wait for the subsidiary SSE debug log notification
        client.waitForNotifications(1);

        // --- Registration: suppressed feature followed by a normal control feature of a different type ---

        toolManager.newTool("quiet-tool")
                .setDescription("quiet tool")
                .setNotifyListChanged(false)
                .setHandler(ta -> ToolResponse.success("ok"))
                .register();
        promptManager.newPrompt("loud-prompt")
                .setDescription("loud prompt")
                .setHandler(pa -> PromptResponse.withMessages(PromptMessage.withUserRole("ok")))
                .register();
        assertLastNotification(client, 2, "notifications/prompts/list_changed");

        promptManager.newPrompt("quiet-prompt")
                .setDescription("quiet prompt")
                .setNotifyListChanged(false)
                .setHandler(pa -> PromptResponse.withMessages(PromptMessage.withUserRole("ok")))
                .register();
        toolManager.newTool("loud-tool")
                .setDescription("loud tool")
                .setHandler(ta -> ToolResponse.success("ok"))
                .register();
        assertLastNotification(client, 3, "notifications/tools/list_changed");

        resourceManager.newResource("quiet-res")
                .setUri("file://quiet-res")
                .setDescription("quiet resource")
                .setNotifyListChanged(false)
                .setHandler(ra -> new ResourceResponse(
                        List.of(TextResourceContents.create(ra.requestUri().value(), "ok"))))
                .register();
        promptManager.newPrompt("loud-prompt-2")
                .setDescription("loud prompt 2")
                .setHandler(pa -> PromptResponse.withMessages(PromptMessage.withUserRole("ok")))
                .register();
        assertLastNotification(client, 4, "notifications/prompts/list_changed");

        resourceTemplateManager.newResourceTemplate("quiet-rt")
                .setUriTemplate("file://quiet-rt/{foo}")
                .setDescription("quiet resource template")
                .setNotifyListChanged(false)
                .setHandler(args -> null)
                .register();
        toolManager.newTool("loud-tool-2")
                .setDescription("loud tool 2")
                .setHandler(ta -> ToolResponse.success("ok"))
                .register();
        assertLastNotification(client, 5, "notifications/tools/list_changed");

        // The caller can still trigger a notification explicitly for a suppressed feature
        toolManager.notifyListChanged(conn -> true);
        assertLastNotification(client, 6, "notifications/tools/list_changed");

        // --- Removal: removing a suppressed feature is silent; the control removal still notifies ---

        assertNotNull(toolManager.removeTool("quiet-tool"));
        assertNotNull(promptManager.removePrompt("loud-prompt"));
        assertLastNotification(client, 7, "notifications/prompts/list_changed");

        assertNotNull(promptManager.removePrompt("quiet-prompt"));
        assertNotNull(toolManager.removeTool("loud-tool"));
        assertLastNotification(client, 8, "notifications/tools/list_changed");

        assertNotNull(resourceManager.removeResource("file://quiet-res"));
        assertNotNull(promptManager.removePrompt("loud-prompt-2"));
        assertLastNotification(client, 9, "notifications/prompts/list_changed");

        assertNotNull(resourceTemplateManager.removeResourceTemplate("quiet-rt"));
        assertNotNull(toolManager.removeTool("loud-tool-2"));
        assertLastNotification(client, 10, "notifications/tools/list_changed");

        client.disconnect();
    }

    private static void assertLastNotification(McpStreamableTestClient client, int expectedCount, String expectedMethod) {
        List<JsonObject> notifications = client.waitForNotifications(expectedCount).notifications();
        assertEquals(expectedCount, notifications.size());
        assertEquals(expectedMethod, notifications.get(expectedCount - 1).getString("method"));
    }
}
