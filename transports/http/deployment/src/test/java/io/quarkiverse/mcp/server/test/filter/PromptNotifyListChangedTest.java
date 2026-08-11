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
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptFilter;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptManager.PromptInfo;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class PromptNotifyListChangedTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyPrompts.class, MyPromptFilter.class));

    @Inject
    PromptManager promptManager;

    @Inject
    MyPromptFilter myFilter;

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

        // Both clients see only "visible" initially
        client1.when()
                .promptsList(page -> {
                    assertEquals(1, page.size());
                    assertEquals("visible", page.prompts().get(0).name());
                })
                .thenAssertResults();

        client2.when()
                .promptsList(page -> {
                    assertEquals(1, page.size());
                    assertEquals("visible", page.prompts().get(0).name());
                })
                .thenAssertResults();

        // Enable "hidden" for client1 only
        myFilter.enabledConnections.add(client1.mcpSessionId());

        // Notify only client1
        promptManager.notifyListChanged(conn -> conn.id().equals(client1.mcpSessionId()));

        // Client1 should receive the notification (2nd notification after the debug log)
        List<JsonObject> notifications = client1.waitForNotifications(2).notifications();
        assertEquals("notifications/prompts/list_changed", notifications.get(1).getString("method"));

        // Client1 now sees both prompts
        client1.when()
                .promptsList(page -> assertEquals(2, page.size()))
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

        // Subscribe to prompts/list_changed
        JsonObject listenRequest = client1.newRequest("subscriptions/listen");
        listenRequest.put("params", new JsonObject()
                .put("notifications", new JsonObject().put("promptsListChanged", true)));
        McpAssured.injectStatelessMeta(listenRequest);
        client1.sendAndForget(listenRequest);
        client1.waitForNotifications(1); // acknowledged

        // Notify all connections
        promptManager.notifyListChanged(conn -> true);

        List<JsonObject> notifications = client1.waitForNotifications(2).notifications();
        assertEquals("notifications/prompts/list_changed", notifications.get(1).getString("method"));

        client1.disconnect();
    }

    public static class MyPrompts {

        @Prompt
        PromptResponse hidden() {
            return PromptResponse.withMessages(PromptMessage.withUserRole("hidden"));
        }

        @Prompt
        PromptResponse visible() {
            return PromptResponse.withMessages(PromptMessage.withUserRole("visible"));
        }
    }

    @Singleton
    public static class MyPromptFilter implements PromptFilter {

        final Set<String> enabledConnections = ConcurrentHashMap.newKeySet();

        @Override
        public boolean test(PromptInfo prompt, McpConnection connection) {
            if (prompt.name().equals("hidden")) {
                return enabledConnections.contains(connection.id());
            }
            return true;
        }
    }
}
