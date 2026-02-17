package io.quarkiverse.mcp.server.test.connectionstore;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ConnectionStore;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class CustomConnectionStoreTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(TrackingConnectionStore.class));

    @Test
    public void testCustomConnectionStore() throws InterruptedException {
        // Connect a client — should trigger put()
        McpSseTestClient client = McpAssured.newConnectedSseClient();

        assertTrue(TrackingConnectionStore.PUT_LATCH.await(5, TimeUnit.SECONDS),
                "put() was not called on the custom store");

        // Close the connection — should trigger remove()
        client.when()
                .message(client.newRequest(McpMethod.Q_CLOSE.jsonRpcName()))
                .send()
                .thenAssertResults();

        assertTrue(TrackingConnectionStore.REMOVE_LATCH.await(5, TimeUnit.SECONDS),
                "remove() was not called on the custom store");
    }

    @Singleton
    public static class TrackingConnectionStore implements ConnectionStore {

        static final CountDownLatch PUT_LATCH = new CountDownLatch(1);
        static final CountDownLatch REMOVE_LATCH = new CountDownLatch(1);

        private final ConcurrentMap<String, McpConnection> connections = new ConcurrentHashMap<>();

        @Override
        public void put(McpConnection connection) {
            connections.put(connection.id(), connection);
            PUT_LATCH.countDown();
        }

        @Override
        public McpConnection get(String id) {
            return connections.get(id);
        }

        @Override
        public McpConnection remove(String id) {
            McpConnection removed = connections.remove(id);
            if (removed != null) {
                REMOVE_LATCH.countDown();
            }
            return removed;
        }

        @Override
        public boolean contains(String id) {
            return connections.containsKey(id);
        }

        @Override
        public Collection<McpConnection> connections() {
            return List.copyOf(connections.values());
        }

        @Override
        public int size() {
            return connections.size();
        }
    }
}
