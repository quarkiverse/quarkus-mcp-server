package io.quarkiverse.mcp.server.test.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URISyntaxException;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.test.McpServerTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public abstract class ProgressTest extends McpServerTest {

    @Test
    public void testNotifications() throws URISyntaxException {
        String token = "abcd";
        initClient();
        JsonObject message = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "longRunning")
                        .put("_meta", new JsonObject().put("progressToken", token)));
        send(message);
        List<JsonObject> notifications = client().waitForNotifications(10);
        assertProgressNotification(notifications.get(0), token, 1, 10.2, "Long running progress: 1");
        assertProgressNotification(notifications.get(1), token, 2, 10.2, "Long running progress: 2");
        assertProgressNotification(notifications.get(9), token, 10, 10.2, "Long running progress: 10");

        JsonObject response = waitForLastResponse();
        JsonObject result = assertResponseMessage(message, response);
        assertNotNull(result);
        assertFalse(result.getBoolean("isError"));
        JsonArray content = result.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals("ok", textContent.getString("text"));
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
