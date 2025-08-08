package io.quarkiverse.mcp.server.test.roots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Notification;
import io.quarkiverse.mcp.server.Notification.Type;
import io.quarkiverse.mcp.server.Root;
import io.quarkiverse.mcp.server.Roots;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured.McpTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public abstract class AbstractRootsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    protected abstract McpTestClient<?, ?> testClient();

    @Test
    public void testRoots() throws InterruptedException {
        McpTestClient<?, ?> client = testClient();

        // The server should list the roots
        List<JsonObject> requests = client.waitForRequests(1).requests();
        assertEquals("roots/list", requests.get(0).getString("method"));
        Long id = requests.get(0).getLong("id");
        JsonObject message = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("result", new JsonObject()
                        .put("roots", new JsonArray().add(new JsonObject()
                                .put("uri", "file:///home/file")
                                .put("name", "important file"))));
        // Send the response back to the server
        client.sendAndForget(message);

        assertTrue(MyTools.INIT_LATCH.await(5, TimeUnit.SECONDS));

        client.when()
                .toolsCall("firstRoot", r -> {
                    assertEquals("file:///home/file", r.content().get(0).asText().text());
                })
                .thenAssertResults();

        JsonObject notification = client.newMessage("notifications/roots/list_changed");
        client.sendAndForget(notification);

        // The server should list the roots again
        requests = client.waitForRequests(2).requests();
        assertEquals("roots/list", requests.get(1).getString("method"));
        message = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("id", requests.get(1).getLong("id"))
                .put("result", new JsonObject()
                        .put("roots", new JsonArray().add(new JsonObject()
                                .put("uri", "file:///home/directory")
                                .put("name", "important directory"))));
        // Send the response back to the server
        client.sendAndForget(message);

        assertTrue(MyTools.CHANGE_LATCH.await(5, TimeUnit.SECONDS));

        client.when()
                .toolsCall("firstRoot", r -> {
                    assertEquals("file:///home/directory", r.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

    @Singleton
    public static class MyTools {

        static final CountDownLatch INIT_LATCH = new CountDownLatch(1);
        static final CountDownLatch CHANGE_LATCH = new CountDownLatch(1);

        private final Map<String, List<Root>> rootsMap = new ConcurrentHashMap<>();

        @Notification(Type.INITIALIZED)
        void init(McpConnection connection, Roots roots) {
            if (roots.isSupported()) {
                rootsMap.put(connection.id(), roots.listAndAwait());
            }
            INIT_LATCH.countDown();
        }

        @Notification(Type.ROOTS_LIST_CHANGED)
        void change(McpConnection connection, Roots roots) {
            rootsMap.put(connection.id(), roots.listAndAwait());
            CHANGE_LATCH.countDown();
        }

        @Tool
        TextContent firstRoot(McpConnection connection) {
            return new TextContent(rootsMap.get(connection.id()).get(0).uri());
        }

    }

}
