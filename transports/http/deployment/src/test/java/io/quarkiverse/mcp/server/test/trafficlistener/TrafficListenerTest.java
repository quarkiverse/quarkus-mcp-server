package io.quarkiverse.mcp.server.test.trafficlistener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpTrafficListener;
import io.quarkiverse.mcp.server.RawMessage;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class TrafficListenerTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, TestTrafficListener.class));

    @Inject
    TestTrafficListener listener;

    @Test
    public void testTrafficListener() {
        listener.clear();

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("echo", Map.of("value", "hello"), response -> {
                    assertFalse(response.isError());
                    assertEquals(1, response.content().size());
                    assertEquals("hello", response.firstContent().asText().text());
                })
                .thenAssertResults();

        assertFalse(listener.received.isEmpty(), "Should have received messages");
        assertFalse(listener.sent.isEmpty(), "Should have sent messages");

        assertTrue(listener.received.stream().anyMatch(m -> "tools/call".equals(m.method())),
                "Received messages should contain tools/call request");
        assertTrue(listener.sent.stream().anyMatch(m -> m.asString().contains("hello")),
                "Sent messages should contain tool result");

        assertEquals(client.mcpSessionId(), listener.receivedConnections.get(0).id());

        // Notifications have a null id and a method starting with "notifications/"
        assertTrue(listener.received.stream()
                .filter(m -> m.method() != null && m.method().startsWith("notifications/"))
                .allMatch(m -> m.id() == null),
                "Received notifications should have a null id");
        // Responses have no method
        assertTrue(listener.sent.stream()
                .filter(m -> m.id() != null)
                .allMatch(m -> m.method() == null),
                "Sent responses should have a null method");
        // Every received request has a matching sent response with the same id
        for (RawMessage received : listener.received) {
            if (received.id() != null) {
                assertTrue(listener.sent.stream()
                        .anyMatch(m -> received.id().equals(m.id())),
                        "Sent messages should contain a response with id " + received.id());
            }
        }
    }

    public static class MyTools {

        @Tool
        String echo(String value) {
            return value;
        }
    }

    // CDI scope is added automatically
    public static class TestTrafficListener implements McpTrafficListener {

        final List<RawMessage> received = new CopyOnWriteArrayList<>();
        final List<RawMessage> sent = new CopyOnWriteArrayList<>();
        final List<McpConnection> receivedConnections = new CopyOnWriteArrayList<>();

        @Override
        public void onMessageReceived(RawMessage message, McpConnection connection) {
            received.add(message);
            receivedConnections.add(connection);
        }

        @Override
        public void onMessageSent(RawMessage message, McpConnection connection) {
            sent.add(message);
        }

        void clear() {
            received.clear();
            sent.clear();
            receivedConnections.clear();
        }
    }
}
