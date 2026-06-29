package io.quarkiverse.mcp.server.test.mcpjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class McpJavaCancellationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(McpJavaCancellationFeatures.class));

    @Test
    public void testCancellationPoll() throws InterruptedException {
        assertCancellation("cancelPoll", McpJavaCancellationFeatures.POLL_LATCH);
        assertEquals("No reason at all", McpJavaCancellationFeatures.CANCEL_REASON.get().orElse(null));
    }

    @Test
    public void testCancellationSkip() throws InterruptedException {
        assertCancellation("cancelSkip", McpJavaCancellationFeatures.SKIP_LATCH);
    }

    private void assertCancellation(String toolName, CountDownLatch latch) throws InterruptedException {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        McpJavaCancellationFeatures.CANCELLED.set(false);
        McpJavaCancellationFeatures.CANCEL_REASON.set(null);

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", toolName));
        client.sendAndForget(request);

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        JsonObject notification = client.newMessage("notifications/cancelled").put("params",
                new JsonObject()
                        .put("requestId", request.getValue("id"))
                        .put("reason", "No reason at all"));
        client.sendAndForget(notification);

        Awaitility.await().until(() -> McpJavaCancellationFeatures.CANCELLED.get());
        // Only the response to the "initialize" request
        assertEquals(1, client.snapshot().responses().size());
    }
}
