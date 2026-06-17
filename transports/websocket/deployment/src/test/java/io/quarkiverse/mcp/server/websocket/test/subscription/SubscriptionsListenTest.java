package io.quarkiverse.mcp.server.websocket.test.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpWebSocketTestClient;
import io.quarkiverse.mcp.server.websocket.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class SubscriptionsListenTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(MyTools.class, MyResources.class));

    @Inject
    ToolManager toolManager;

    @Inject
    ConnectionManager connectionManager;

    @Test
    public void testToolsListChanged() {
        McpWebSocketTestClient client = McpAssured.newWebSocketClient()
                .setStateless()
                .build()
                .connect();

        JsonObject listenRequest = client.newRequest("subscriptions/listen");
        listenRequest.put("params", new JsonObject()
                .put("notifications", new JsonObject().put("toolsListChanged", true)));
        McpAssured.injectStatelessMeta(listenRequest);
        client.sendAndForget(listenRequest);

        List<JsonObject> notifications = client.waitForNotifications(1).notifications();
        JsonObject ack = notifications.get(0);
        assertEquals("notifications/subscriptions/acknowledged", ack.getString("method"));
        JsonObject ackParams = ack.getJsonObject("params");
        assertNotNull(ackParams);
        assertTrue(ackParams.getJsonObject("notifications").getBoolean("toolsListChanged"));
        JsonObject meta = ackParams.getJsonObject("_meta");
        assertNotNull(meta);
        assertNotNull(meta.getValue("io.modelcontextprotocol/subscriptionId"));

        // Transient connection should be registered in ConnectionManager
        assertTrue(connectionManager.iterator().hasNext());

        toolManager.newTool("dynamic")
                .setDescription("A dynamically registered tool")
                .setHandler(args -> ToolResponse.success(new TextContent("dynamic")))
                .register();

        notifications = client.waitForNotifications(2).notifications();
        JsonObject listChanged = notifications.get(1);
        assertEquals("notifications/tools/list_changed", listChanged.getString("method"));
        JsonObject listChangedMeta = listChanged.getJsonObject("params").getJsonObject("_meta");
        assertNotNull(listChangedMeta);
        assertEquals(listenRequest.getValue("id"),
                listChangedMeta.getValue("io.modelcontextprotocol/subscriptionId"));

        client.disconnect();
    }

    @Test
    public void testFilteringNonMatching() {
        McpWebSocketTestClient client = McpAssured.newWebSocketClient()
                .setStateless()
                .build()
                .connect();

        JsonObject listenRequest = client.newRequest("subscriptions/listen");
        listenRequest.put("params", new JsonObject()
                .put("notifications", new JsonObject().put("promptsListChanged", true)));
        McpAssured.injectStatelessMeta(listenRequest);
        client.sendAndForget(listenRequest);

        client.waitForNotifications(1);

        toolManager.newTool("filteredTool")
                .setDescription("Should not trigger notification on this subscription")
                .setHandler(args -> ToolResponse.success(new TextContent("filtered")))
                .register();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertEquals(1, client.snapshot().notifications().size());

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
