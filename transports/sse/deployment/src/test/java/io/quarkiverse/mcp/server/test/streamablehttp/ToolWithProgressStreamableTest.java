package io.quarkiverse.mcp.server.test.streamablehttp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ToolWithProgressStreamableTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testToolWithProgress() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        String token = "abcd";

        client.when()
                .toolsCall("bravo")
                .withArguments(Map.of("price", 10))
                .withMetadata(Map.of("progressToken", token))
                .withAssert(r -> {
                    assertEquals("420", r.content().get(0).asText().text());
                })
                .send()
                .thenAssertResults();

        List<JsonObject> notifications = client.waitForNotifications(1).notifications();
        assertProgressNotification(notifications.get(0), token, 1, 1, null);
    }

    protected void assertProgressNotification(JsonObject notification, String token, int progress, double total,
            String message) {
        JsonObject params = notification.getJsonObject("params");
        assertEquals(token, params.getString("progressToken"));
        assertEquals(progress, params.getInteger("progress"));
        assertEquals(total, params.getDouble("total"));
        assertEquals(message, params.getString("message"));
    }

    public static class MyTools {

        @Tool
        String bravo(int price, Progress progress) {
            progress.notificationBuilder().setTotal(1).setProgress(1).build().sendAndForget();
            return "" + price * 42;
        }
    }

}
