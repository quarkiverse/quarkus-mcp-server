package io.quarkiverse.mcp.server.test.streamablehttp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class SubsidiarySseTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Inject
    ToolManager toolManager;

    @Test
    public void testTerminateSession() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setOpenSubsidiarySse(true)
                .build()
                .connect();

        List<JsonObject> notifications = client.waitForNotifications(1).notifications();
        assertEquals("notifications/message", notifications.get(0).getString("method"));

        // New tool -> notification should be sent
        toolManager.newTool("charlie")
                .setDescription("Charlie")
                .setHandler(ta -> ToolResponse.success("charlie!"))
                .register();

        notifications = client.waitForNotifications(2).notifications();
        assertEquals("notifications/tools/list_changed", notifications.get(1).getString("method"));

        client.when()
                .toolsCall("charlie", toolResponse -> {
                    assertEquals("charlie!", toolResponse.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        String bravo(int price) {
            return "" + price * 42;
        }
    }

}
