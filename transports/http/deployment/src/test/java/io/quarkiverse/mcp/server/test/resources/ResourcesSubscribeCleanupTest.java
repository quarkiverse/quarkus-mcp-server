package io.quarkiverse.mcp.server.test.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.ResourceSubscriptions;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ResourcesSubscribeCleanupTest extends McpServerTest {

    static final String URI_1 = "file:///cleanup1.txt";
    static final String URI_2 = "file:///cleanup2.txt";

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyResources.class));

    @Inject
    ResourceManager manager;

    @Inject
    ConnectionManager connectionManager;

    @Inject
    ResourceSubscriptions subscriptions;

    @Test
    public void testSubscribeTwiceNotifiesOnce() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        subscribe(client, URI_1);
        subscribe(client, URI_1);
        subscribe(client, URI_2);

        manager.getResource(URI_1).sendUpdateAndForget();
        manager.getResource(URI_2).sendUpdateAndForget();

        // Notifications on one connection keep their order: a duplicate for URI_1 would arrive before URI_2
        List<JsonObject> notifications = client.waitForNotifications(2).notifications();
        assertEquals(URI_1, notifications.get(0).getJsonObject("params").getString("uri"));
        assertEquals(URI_2, notifications.get(1).getJsonObject("params").getString("uri"));
        client.disconnect();
    }

    @Test
    public void testDisconnectRemovesSubscriptions() {
        Set<String> before = subscriptions.subscribers(URI_1);
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        subscribe(client, URI_1);

        Set<String> added = new HashSet<>(subscriptions.subscribers(URI_1));
        added.removeAll(before);
        assertEquals(1, added.size());
        String connectionId = added.iterator().next();
        assertTrue(connectionManager.has(connectionId));

        client.disconnect();
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> !connectionManager.has(connectionId));

        assertFalse(subscriptions.subscribers(URI_1).contains(connectionId));
    }

    private void subscribe(McpSseTestClient client, String uri) {
        JsonObject sub = client.newRequest("resources/subscribe")
                .put("params", new JsonObject().put("uri", uri));
        client.sendAndForget(sub);
        client.waitForResponse(sub);
    }

    public static class MyResources {

        @Resource(uri = URI_1)
        TextResourceContents cleanup1(RequestUri uri) {
            return new TextResourceContents(uri.value(), "1", null);
        }

        @Resource(uri = URI_2)
        TextResourceContents cleanup2(RequestUri uri) {
            return new TextResourceContents(uri.value(), "2", null);
        }
    }
}
