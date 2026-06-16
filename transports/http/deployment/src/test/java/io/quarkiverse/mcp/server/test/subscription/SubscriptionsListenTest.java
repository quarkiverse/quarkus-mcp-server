package io.quarkiverse.mcp.server.test.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class SubscriptionsListenTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(MyTools.class, MyResources.class));

    @Inject
    ToolManager toolManager;

    @Inject
    ResourceManager resourceManager;

    @Inject
    ConnectionManager connectionManager;

    @Test
    public void testToolsListChanged() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        // Send subscriptions/listen with toolsListChanged filter
        JsonObject listenRequest = client.newRequest("subscriptions/listen");
        listenRequest.put("params", new JsonObject()
                .put("notifications", new JsonObject().put("toolsListChanged", true)));
        McpAssured.injectStatelessMeta(listenRequest);
        client.sendAndForget(listenRequest);

        // Wait for the acknowledged notification
        List<JsonObject> notifications = client.waitForNotifications(1).notifications();
        JsonObject ack = notifications.get(0);
        assertEquals("notifications/subscriptions/acknowledged", ack.getString("method"));
        JsonObject ackParams = ack.getJsonObject("params");
        assertNotNull(ackParams);
        assertTrue(ackParams.getJsonObject("notifications").getBoolean("toolsListChanged"));
        // Verify subscriptionId in _meta
        JsonObject meta = ackParams.getJsonObject("_meta");
        assertNotNull(meta);
        assertNotNull(meta.getValue("io.modelcontextprotocol/subscriptionId"));

        // Transient connection should be registered in ConnectionManager
        assertTrue(connectionManager.iterator().hasNext());

        // Register a new tool to trigger notifications/tools/list_changed
        toolManager.newTool("dynamic")
                .setDescription("A dynamically registered tool")
                .setHandler(args -> ToolResponse.success(new TextContent("dynamic")))
                .register();

        // Wait for the list_changed notification
        notifications = client.waitForNotifications(2).notifications();
        JsonObject listChanged = notifications.get(1);
        assertEquals("notifications/tools/list_changed", listChanged.getString("method"));
        // Verify subscriptionId is injected
        JsonObject listChangedParams = listChanged.getJsonObject("params");
        assertNotNull(listChangedParams);
        JsonObject listChangedMeta = listChangedParams.getJsonObject("_meta");
        assertNotNull(listChangedMeta);
        assertEquals(listenRequest.getValue("id"),
                listChangedMeta.getValue("io.modelcontextprotocol/subscriptionId"));

        client.disconnect();
    }

    @Test
    public void testResourceUpdated() {
        String uri = "file:///file1.txt";

        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        // Subscribe to resource updates
        JsonObject listenRequest = client.newRequest("subscriptions/listen");
        listenRequest.put("params", new JsonObject()
                .put("notifications", new JsonObject()
                        .put("resourceSubscriptions", new JsonArray().add(uri))));
        McpAssured.injectStatelessMeta(listenRequest);
        client.sendAndForget(listenRequest);

        // Wait for acknowledged
        client.waitForNotifications(1);

        // Trigger resource update
        resourceManager.getResource(uri).sendUpdateAndForget();

        // Wait for resource updated notification
        List<JsonObject> notifications = client.waitForNotifications(2).notifications();
        JsonObject updated = notifications.get(1);
        assertEquals("notifications/resources/updated", updated.getString("method"));
        assertEquals(uri, updated.getJsonObject("params").getString("uri"));
        // Verify subscriptionId
        JsonObject meta = updated.getJsonObject("params").getJsonObject("_meta");
        assertNotNull(meta);
        assertEquals(listenRequest.getValue("id"),
                meta.getValue("io.modelcontextprotocol/subscriptionId"));

        client.disconnect();
    }

    @Test
    public void testFilteringNonMatching() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        // Subscribe only to promptsListChanged
        JsonObject listenRequest = client.newRequest("subscriptions/listen");
        listenRequest.put("params", new JsonObject()
                .put("notifications", new JsonObject().put("promptsListChanged", true)));
        McpAssured.injectStatelessMeta(listenRequest);
        client.sendAndForget(listenRequest);

        // Wait for acknowledged
        client.waitForNotifications(1);

        // Trigger a tools list change — should NOT be delivered
        toolManager.newTool("filteredTool")
                .setDescription("Should not trigger notification on this subscription")
                .setHandler(args -> ToolResponse.success(new TextContent("filtered")))
                .register();

        // Give some time for any notification to arrive (it shouldn't)
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Should still only have the acknowledged notification
        assertEquals(1, client.snapshot().notifications().size());

        client.disconnect();
    }

    @Test
    public void testOldSubscribeBlockedForDraftProtocol() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        // resources/subscribe should be blocked for stateless protocol
        JsonObject subscribeRequest = client.newRequest("resources/subscribe");
        subscribeRequest.put("params", new JsonObject().put("uri", "file:///file1.txt"));
        McpAssured.injectStatelessMeta(subscribeRequest);

        client.when()
                .message(subscribeRequest)
                .withErrorAssert(error -> {
                    assertEquals(-32601, error.code());
                })
                .send()
                .thenAssertResults();

        client.disconnect();
    }

    public static class MyTools {

        @Tool
        String echo(String message) {
            return message;
        }
    }

    public static class MyResources {

        @Resource(uri = "file:///file1.txt")
        TextResourceContents file1(RequestUri uri) {
            return new TextResourceContents(uri.value(), "File 1", null);
        }
    }

}
