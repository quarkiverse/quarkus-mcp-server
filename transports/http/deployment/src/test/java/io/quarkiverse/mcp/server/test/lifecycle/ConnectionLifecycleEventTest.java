package io.quarkiverse.mcp.server.test.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpConnectionEvent;
import io.quarkiverse.mcp.server.McpConnectionEvent.Type;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ConnectionLifecycleEventTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(ConnectionEventObserver.class));

    @Test
    public void testConnectionLifecycleEvents() throws InterruptedException {
        // Connect a client — triggers INITIALIZING and INITIALIZED
        McpSseTestClient client = McpAssured.newConnectedSseClient();

        assertTrue(ConnectionEventObserver.INITIALIZED_LATCH.await(5, TimeUnit.SECONDS),
                "INITIALIZED event not received in time");

        // Verify INITIALIZING and INITIALIZED events were received
        List<McpConnectionEvent> events = ConnectionEventObserver.EVENTS;
        assertTrue(events.size() >= 2, "Expected at least 2 events, got: " + events.size());

        McpConnectionEvent initializingEvent = events.stream()
                .filter(e -> e.type() == Type.INITIALIZING).findFirst().orElse(null);
        assertNotNull(initializingEvent, "INITIALIZING event not found");
        assertNotNull(initializingEvent.connection().initialRequest(), "initialRequest should be available on INITIALIZING");
        assertNotNull(initializingEvent.connection().initialRequest().implementation(),
                "implementation should be available on INITIALIZING");

        McpConnectionEvent initializedEvent = events.stream()
                .filter(e -> e.type() == Type.INITIALIZED).findFirst().orElse(null);
        assertNotNull(initializedEvent, "INITIALIZED event not found");

        // Both events should reference the same connection id
        assertEquals(initializingEvent.connection().id(), initializedEvent.connection().id());

        // Close the connection — triggers CLOSED
        client.when()
                .message(client.newRequest(McpMethod.Q_CLOSE.jsonRpcName()))
                .send()
                .thenAssertResults();

        assertTrue(ConnectionEventObserver.CLOSED_LATCH.await(5, TimeUnit.SECONDS),
                "CLOSED event not received in time");

        McpConnectionEvent closedEvent = events.stream()
                .filter(e -> e.type() == Type.CLOSED).findFirst().orElse(null);
        assertNotNull(closedEvent, "CLOSED event not found");
        assertEquals(initializingEvent.connection().id(), closedEvent.connection().id());
    }

    @Singleton
    public static class ConnectionEventObserver {

        static final List<McpConnectionEvent> EVENTS = new CopyOnWriteArrayList<>();
        static final CountDownLatch INITIALIZED_LATCH = new CountDownLatch(1);
        static final CountDownLatch CLOSED_LATCH = new CountDownLatch(1);

        void onConnectionEvent(@ObservesAsync McpConnectionEvent event) {
            EVENTS.add(event);
            if (event.type() == Type.INITIALIZED) {
                INITIALIZED_LATCH.countDown();
            } else if (event.type() == Type.CLOSED) {
                CLOSED_LATCH.countDown();
            }
        }
    }
}
