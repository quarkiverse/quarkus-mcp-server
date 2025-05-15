package io.quarkiverse.mcp.server.test.streamablehttp;

import static io.quarkiverse.mcp.server.sse.runtime.StreamableHttpMcpMessageHandler.MCP_SESSION_ID_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.http.HttpRequest.BodyPublishers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.StreamableMcpSseClient;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ToolWithProgressStreamableTest extends StreamableHttpTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testToolWithProgress() {
        String mcpSessionId = initSession();
        String token = "abcd";
        JsonObject msg1 = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "bravo")
                        .put("arguments", new JsonObject()
                                .put("price", 10))
                        .put("_meta", new JsonObject().put("progressToken", token)));

        Map<String, String> headers = new HashMap<>(
                defaultHeaders().entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> e.getValue().toString())));
        headers.put(MCP_SESSION_ID_HEADER, mcpSessionId);

        StreamableMcpSseClient client = new StreamableMcpSseClient(messageEndpoint, BodyPublishers.ofString(msg1.encode()),
                headers);
        client.connect();
        List<JsonObject> notifications = client.waitForNotifications(1);
        assertEquals(1, notifications.size());
        assertProgressNotification(notifications.get(0), token, 1, 1, null);

        List<JsonObject> responses = client.waitForResponses(1);
        assertResponse(responses, msg1, "420");
    }

    private void assertResponse(List<JsonObject> responses, JsonObject msg, String expectedText) {
        JsonObject response = null;
        for (JsonObject r : responses) {
            if (r.getInteger("id") == msg.getInteger("id")) {
                response = r;
                break;
            }
        }
        assertNotNull(response);

        JsonObject result = assertResultResponse(msg, response);
        assertNotNull(result);
        assertFalse(result.getBoolean("isError"));
        JsonArray content = result.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals(expectedText, textContent.getString("text"));
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
