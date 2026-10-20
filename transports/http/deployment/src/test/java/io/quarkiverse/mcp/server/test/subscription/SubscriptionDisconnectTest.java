package io.quarkiverse.mcp.server.test.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class SubscriptionDisconnectTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Inject
    ConnectionManager connections;

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    public void disconnectReleasesSubscriptions(boolean useClose) {
        McpStreamableTestClient client = McpAssured.newStreamableClient().setStateless().build();
        try (McpStreamableTestClient other = McpAssured.newStreamableClient().setStateless().build().connect()) {
            // Reconnecting the same client must also create streams that can be closed.
            for (int attempt = 0; attempt < 2; attempt++) {
                client.connect();
                List<String> connectionIds = new ArrayList<>();
                try {
                    for (int i = 0; i < 2; i++) {
                        JsonObject listen = client.newRequest("subscriptions/listen").put("params",
                                new JsonObject().put("notifications", new JsonObject().put("toolsListChanged", true)));
                        McpAssured.injectStatelessMeta(listen);
                        client.sendAndForget(listen);
                        JsonObject ack = client.waitForNotifications(i + 1).notifications().get(i);
                        assertEquals("notifications/subscriptions/acknowledged", ack.getString("method"));
                    }
                    connections.forEach(connection -> connectionIds.add(connection.id()));
                    assertEquals(2, connectionIds.size());
                    if (useClose) {
                        client.close();
                    } else {
                        client.disconnect();
                    }
                    assertFalse(client.isConnected());
                    Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                        for (String id : connectionIds) {
                            assertFalse(connections.has(id), "Subscription still registered: " + id);
                        }
                    });
                    // Closing one client must not close the shared HTTP client used by others.
                    other.when().toolsList(page -> assertEquals(1, page.size())).thenAssertResults();
                } finally {
                    client.disconnect();
                    connectionIds.forEach(connections::remove);
                }
            }
        }
    }

    @Test
    public void disconnectPreservesSession() {
        McpStreamableTestClient client = McpAssured.newStreamableClient().setOpenSubsidiarySse(true).build().connect();
        String sessionId = client.mcpSessionId();
        try {
            client.disconnect();
            assertFalse(client.isConnected());
            assertTrue(connections.has(sessionId));
        } finally {
            client.disconnect();
            connections.remove(sessionId);
        }
    }

    public static class MyTools {
        @Tool
        String hello() {
            return "hello";
        }
    }
}
