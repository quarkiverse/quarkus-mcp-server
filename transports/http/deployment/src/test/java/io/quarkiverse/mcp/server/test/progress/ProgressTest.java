package io.quarkiverse.mcp.server.test.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.Snapshot;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.vertx.core.json.JsonObject;

public abstract class ProgressTest extends McpServerTest {

    @Test
    public void testNotifications() {
        McpSseTestClient client = McpAssured.newSseClient()
                .build()
                .connect();
        String token = "abcd";

        Snapshot snapshot = client.when()
                .toolsCall("longRunning")
                .withMetadata(Map.of("progressToken", token))
                .withAssert(toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("ok", toolResponse.content().get(0).asText().text());
                })
                .send()
                .thenAssertResults();

        List<JsonObject> notifications = snapshot.notifications();

        assertProgressNotification(notifications.get(0), token, 1, 10.2, "Long running progress: 1");
        assertProgressNotification(notifications.get(1), token, 2, 10.2, "Long running progress: 2");
        assertProgressNotification(notifications.get(9), token, 10, 10.2, "Long running progress: 10");
    }

    protected void assertProgressNotification(JsonObject notification, String token, int progress, double total,
            String message) {
        JsonObject params = notification.getJsonObject("params");
        assertEquals(token, params.getString("progressToken"));
        assertEquals(progress, params.getInteger("progress"));
        assertEquals(total, params.getDouble("total"));
        assertEquals(message, params.getString("message"));
    }

}
