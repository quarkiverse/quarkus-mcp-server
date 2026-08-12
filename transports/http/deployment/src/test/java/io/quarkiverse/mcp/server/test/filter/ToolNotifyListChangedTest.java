package io.quarkiverse.mcp.server.test.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolFilter;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ToolNotifyListChangedTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, MyToolFilter.class));

    @Inject
    ToolManager toolManager;

    @Inject
    MyToolFilter myFilter;

    @Test
    public void testStateful() {
        McpStreamableTestClient client1 = McpAssured.newStreamableClient()
                .setOpenSubsidiarySse(true)
                .build()
                .connect();
        McpStreamableTestClient client2 = McpAssured.newStreamableClient()
                .setOpenSubsidiarySse(true)
                .build()
                .connect();

        // Wait for the subsidiary SSE debug log notification
        client1.waitForNotifications(1);
        client2.waitForNotifications(1);

        // Both clients see only "bravo" initially
        client1.when()
                .toolsList(page -> {
                    assertEquals(1, page.size());
                    assertEquals("bravo", page.tools().get(0).name());
                })
                .thenAssertResults();

        client2.when()
                .toolsList(page -> {
                    assertEquals(1, page.size());
                    assertEquals("bravo", page.tools().get(0).name());
                })
                .thenAssertResults();

        // Enable "alpha" for client1 only
        myFilter.enabledConnections.add(client1.mcpSessionId());

        // Notify only client1
        toolManager.notifyListChanged(conn -> conn.id().equals(client1.mcpSessionId()));

        // Client1 should receive the notification (2nd notification after the debug log)
        List<JsonObject> notifications = client1.waitForNotifications(2).notifications();
        assertEquals("notifications/tools/list_changed", notifications.get(1).getString("method"));

        // Client1 now sees both tools
        client1.when()
                .toolsList(page -> assertEquals(2, page.size()))
                .thenAssertResults();

        // Client2 should still have only the initial debug log notification
        assertEquals(1, client2.snapshot().notifications().size());

        client1.disconnect();
        client2.disconnect();
    }

    @Test
    public void testStateless() {
        McpStreamableTestClient client1 = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();
        McpStreamableTestClient client2 = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        // Subscribe client1 to tools/list_changed
        JsonObject listenRequest1 = client1.newRequest("subscriptions/listen");
        listenRequest1.put("params", new JsonObject()
                .put("notifications", new JsonObject().put("toolsListChanged", true)));
        McpAssured.injectStatelessMeta(listenRequest1);
        client1.sendAndForget(listenRequest1);
        client1.waitForNotifications(1); // acknowledged

        // Subscribe client2 to tools/list_changed
        JsonObject listenRequest2 = client2.newRequest("subscriptions/listen");
        listenRequest2.put("params", new JsonObject()
                .put("notifications", new JsonObject().put("toolsListChanged", true)));
        McpAssured.injectStatelessMeta(listenRequest2);
        client2.sendAndForget(listenRequest2);
        client2.waitForNotifications(1); // acknowledged

        // Notify all connections — both should receive
        toolManager.notifyListChanged(conn -> true);

        List<JsonObject> notifications1 = client1.waitForNotifications(2).notifications();
        assertEquals("notifications/tools/list_changed", notifications1.get(1).getString("method"));
        // Verify subscriptionId is injected
        JsonObject meta1 = notifications1.get(1).getJsonObject("params").getJsonObject("_meta");
        assertNotNull(meta1);
        assertNotNull(meta1.getValue("io.modelcontextprotocol/subscriptionId"));

        List<JsonObject> notifications2 = client2.waitForNotifications(2).notifications();
        assertEquals("notifications/tools/list_changed", notifications2.get(1).getString("method"));

        client1.disconnect();
        client2.disconnect();
    }

    public static class MyTools {

        @Tool
        ToolResponse alpha(String value) {
            return ToolResponse.success(value);
        }

        @Tool
        ToolResponse bravo(String value) {
            return ToolResponse.success(value);
        }
    }

    @Singleton
    public static class MyToolFilter implements ToolFilter {

        final Set<String> enabledConnections = ConcurrentHashMap.newKeySet();

        @Override
        public boolean test(ToolInfo tool, McpConnection connection) {
            if (tool.name().equals("alpha")) {
                return enabledConnections.contains(connection.id());
            }
            return true;
        }
    }
}
