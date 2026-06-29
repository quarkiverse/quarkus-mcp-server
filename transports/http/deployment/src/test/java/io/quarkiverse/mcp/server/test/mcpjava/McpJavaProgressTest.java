package io.quarkiverse.mcp.server.test.mcpjava;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class McpJavaProgressTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(McpJavaProgressFeatures.class));

    @Test
    public void testProgress() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        String notifToken = "token-notif";
        String trackerToken = "token-tracker";
        String promptToken = "token-prompt";

        client.when()
                .toolsCall("progressNotification")
                .withArguments(Map.of("val", "hello"))
                .withMetadata(Map.of("progressToken", notifToken))
                .withAssert(r -> assertEquals("notification:hello", r.firstContent().asText().text()))
                .send()
                .toolsCall("progressTracker")
                .withArguments(Map.of("val", "world"))
                .withMetadata(Map.of("progressToken", trackerToken))
                .withAssert(r -> assertEquals("tracker:world", r.firstContent().asText().text()))
                .send()
                .promptsGet("promptWithProgress")
                .withArguments(Map.of("name", "Martin"))
                .withMetadata(Map.of("progressToken", promptToken))
                .withAssert(r -> assertEquals("Hello Martin!", r.firstMessage().content().asText().text()))
                .send()
                .thenAssertResults();

        // 1 notification from progressNotification + 2 from progressTracker + 1 from promptWithProgress
        List<JsonObject> notifications = client.waitForNotifications(4).notifications();
        assertNotification(notifications, notifToken, 1, 5, "step 1");
        assertNotification(notifications, trackerToken, 1, 3, "progress: 1");
        assertNotification(notifications, trackerToken, 2, 3, "progress: 2");
        assertNotification(notifications, promptToken, 1, 1, null);
    }

    private void assertNotification(List<JsonObject> notifications, String token, int progress, double total,
            String message) {
        JsonObject match = notifications.stream()
                .filter(n -> {
                    JsonObject params = n.getJsonObject("params");
                    return token.equals(params.getString("progressToken"))
                            && progress == params.getInteger("progress")
                            && total == params.getDouble("total");
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No notification found with token=" + token + ", progress=" + progress + ", total=" + total));
        assertEquals(message, match.getJsonObject("params").getString("message"));
    }

    @Test
    public void testProgressToken() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        String tokenVal = "my-token";

        client.when()
                .toolsCall("progressTokenInfo")
                .withArguments(Map.of("val", "x"))
                .withMetadata(Map.of("progressToken", tokenVal))
                .withAssert(r -> assertEquals("token:STRING:" + tokenVal, r.firstContent().asText().text()))
                .send()
                .thenAssertResults();
    }

    @Test
    public void testProgressTokenInteger() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("progressTokenInfo")
                .withArguments(Map.of("val", "x"))
                .withMetadata(Map.of("progressToken", 42))
                .withAssert(r -> assertEquals("token:INTEGER:42", r.firstContent().asText().text()))
                .send()
                .thenAssertResults();
    }

    @Test
    public void testProgressTokenAbsent() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("progressTokenInfo")
                .withArguments(Map.of("val", "x"))
                .withAssert(r -> assertEquals("token:absent", r.firstContent().asText().text()))
                .send()
                .thenAssertResults();
    }
}
