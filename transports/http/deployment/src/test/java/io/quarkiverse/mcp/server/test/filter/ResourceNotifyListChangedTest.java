package io.quarkiverse.mcp.server.test.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceFilter;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceManager.ResourceInfo;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ResourceNotifyListChangedTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyResources.class, MyResourceFilter.class));

    @Inject
    ResourceManager resourceManager;

    @Inject
    MyResourceFilter myFilter;

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

        // Both clients see only "open" initially
        client1.when()
                .resourcesList(page -> {
                    assertEquals(1, page.size());
                    assertEquals("open", page.resources().get(0).name());
                })
                .thenAssertResults();

        client2.when()
                .resourcesList(page -> {
                    assertEquals(1, page.size());
                    assertEquals("open", page.resources().get(0).name());
                })
                .thenAssertResults();

        // Enable "restricted" for client1 only
        myFilter.enabledConnections.add(client1.mcpSessionId());

        // Notify only client1
        resourceManager.notifyListChanged(conn -> conn.id().equals(client1.mcpSessionId()));

        // Client1 should receive the notification (2nd notification after the debug log)
        List<JsonObject> notifications = client1.waitForNotifications(2).notifications();
        assertEquals("notifications/resources/list_changed", notifications.get(1).getString("method"));

        // Client1 now sees both resources
        client1.when()
                .resourcesList(page -> assertEquals(2, page.size()))
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

        // Subscribe to resources/list_changed
        JsonObject listenRequest = client1.newRequest("subscriptions/listen");
        listenRequest.put("params", new JsonObject()
                .put("notifications", new JsonObject().put("resourcesListChanged", true)));
        McpAssured.injectStatelessMeta(listenRequest);
        client1.sendAndForget(listenRequest);
        client1.waitForNotifications(1); // acknowledged

        // Notify all connections
        resourceManager.notifyListChanged(conn -> true);

        List<JsonObject> notifications = client1.waitForNotifications(2).notifications();
        assertEquals("notifications/resources/list_changed", notifications.get(1).getString("method"));

        client1.disconnect();
    }

    public static class MyResources {

        @Resource(uri = "file:///restricted")
        TextResourceContents restricted(RequestUri uri) {
            return TextResourceContents.create(uri.value(), "restricted content");
        }

        @Resource(uri = "file:///open")
        TextResourceContents open(RequestUri uri) {
            return TextResourceContents.create(uri.value(), "open content");
        }
    }

    @Singleton
    public static class MyResourceFilter implements ResourceFilter {

        final Set<String> enabledConnections = ConcurrentHashMap.newKeySet();

        @Override
        public boolean test(ResourceInfo resource, McpConnection connection) {
            if (resource.name().equals("restricted")) {
                return enabledConnections.contains(connection.id());
            }
            return true;
        }
    }
}
