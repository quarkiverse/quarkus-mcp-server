package io.quarkiverse.mcp.server.schema.validator.test.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.schema.validator.test.McpServerTest;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class NotificationsSchemaValidationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testCancelledNotification() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        // Valid notifications/cancelled - this is a notification so no response expected
        JsonObject validCancelled = client.newMessage("notifications/cancelled").put("params",
                new JsonObject()
                        .put("requestId", 999)
                        .put("reason", "User cancelled"));
        client.sendAndForget(validCancelled);

        // Invalid notifications/cancelled - missing required "requestId" field
        // Notifications don't return errors to the client, but the validator should still process them.
        // We test here that valid notifications are accepted without issues.
        client.when()
                .toolsCall("dummy", response -> {
                    assertEquals("dummy", response.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

    @Test
    public void testProgressNotification() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        // Valid notifications/progress
        JsonObject validProgress = client.newMessage("notifications/progress").put("params",
                new JsonObject()
                        .put("progressToken", "token123")
                        .put("progress", 5)
                        .put("total", 10));
        client.sendAndForget(validProgress);

        // Verify the server still works after receiving the notification
        client.when()
                .toolsCall("dummy", response -> {
                    assertEquals("dummy", response.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

    @Test
    public void testRootsListChangedNotification() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        // Valid notifications/roots/list_changed - no params required
        JsonObject validRootsChanged = client.newMessage("notifications/roots/list_changed");
        client.sendAndForget(validRootsChanged);

        // Verify the server still works after receiving the notification
        client.when()
                .toolsCall("dummy", response -> {
                    assertEquals("dummy", response.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        String dummy() {
            return "dummy";
        }
    }
}
