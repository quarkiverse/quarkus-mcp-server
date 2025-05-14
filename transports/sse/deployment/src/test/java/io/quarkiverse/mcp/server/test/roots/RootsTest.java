package io.quarkiverse.mcp.server.test.roots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RootsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testRoots() throws InterruptedException {
        initClient();
        // The server should list the roots
        List<JsonObject> requests = client().waitForRequests(1);
        assertEquals("roots/list", requests.get(0).getString("method"));
        Long id = requests.get(0).getLong("id");
        JsonObject message = newResult(id, new JsonObject()
                .put("roots", new JsonArray().add(new JsonObject()
                        .put("uri", "file:///home/file")
                        .put("name", "important file"))));
        // Send the response back to the server
        send(message);

        assertTrue(MyTools.INIT_LATCH.await(5, TimeUnit.SECONDS));

        JsonObject toolCallMessage = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "firstRoot"));
        send(toolCallMessage);
        JsonObject toolCallResponse = waitForLastResponse();
        JsonObject toolCallResult = assertResultResponse(toolCallMessage, toolCallResponse);
        assertNotNull(toolCallResult);
        assertFalse(toolCallResult.getBoolean("isError"));
        JsonArray content = toolCallResult.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals("file:///home/file", textContent.getString("text"));

        client().clearRequests();

        JsonObject notification = newNotification("notifications/roots/list_changed");
        send(notification);

        // The server should list the roots again
        requests = client().waitForRequests(1);
        assertEquals("roots/list", requests.get(0).getString("method"));
        message = newResult(requests.get(0).getLong("id"), new JsonObject()
                .put("roots", new JsonArray().add(new JsonObject()
                        .put("uri", "file:///home/directory")
                        .put("name", "important directory"))));
        // Send the response back to the server
        send(message);

        assertTrue(MyTools.CHANGE_LATCH.await(5, TimeUnit.SECONDS));

        toolCallMessage = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "firstRoot"));
        send(toolCallMessage);
        toolCallResponse = waitForLastResponse();
        toolCallResult = assertResultResponse(toolCallMessage, toolCallResponse);
        assertNotNull(toolCallResult);
        assertFalse(toolCallResult.getBoolean("isError"));
        content = toolCallResult.getJsonArray("content");
        assertEquals(1, content.size());
        textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals("file:///home/directory", textContent.getString("text"));
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

    @Override
    protected JsonObject getClientCapabilities() {
        return new JsonObject().put("roots", Map.of());
    }

}
