package io.quarkiverse.mcp.server.test.roots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Notification;
import io.quarkiverse.mcp.server.Notification.Type;
import io.quarkiverse.mcp.server.Roots;
import io.quarkiverse.mcp.server.runtime.ResponseHandlers;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

public class RootsTimeoutTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.roots.default-timeout", "1s");

    @Inject
    ResponseHandlers responseHandlers;

    @Test
    public void testRoots() throws InterruptedException {
        initClient();
        // The server should list the roots 2x
        List<JsonObject> requests = client().waitForRequests(2);
        assertEquals("roots/list", requests.get(0).getString("method"));
        assertEquals("roots/list", requests.get(1).getString("method"));
        Long id1 = requests.get(0).getLong("id");
        Long id2 = requests.get(1).getLong("id");
        // But the client does not respond...
        assertTrue(MyTools.INIT_LATCH1.await(5, TimeUnit.SECONDS));
        assertNotNull(MyTools.ERROR1.get());

        assertFalse(responseHandlers.hasHandler(id1));
        assertFalse(responseHandlers.hasHandler(id2));
    }

    @Singleton
    public static class MyTools {

        static final CountDownLatch INIT_LATCH1 = new CountDownLatch(1);
        static final CountDownLatch INIT_LATCH2 = new CountDownLatch(1);

        static final AtomicReference<Throwable> ERROR1 = new AtomicReference<>();
        static final AtomicReference<Throwable> ERROR2 = new AtomicReference<>();

        @Notification(Type.INITIALIZED)
        void init(McpConnection connection, Roots roots) {
            try {
                roots.listAndAwait();
            } catch (Throwable expected) {
                ERROR1.set(expected);
            }
            INIT_LATCH1.countDown();
        }

        @Notification(Type.INITIALIZED)
        Uni<Void> uniInit(Roots roots) {
            return roots.list()
                    .onFailure().recoverWithItem(t -> {
                        ERROR2.set(t);
                        return null;
                    })
                    .eventually(() -> INIT_LATCH2.countDown())
                    .replaceWithVoid();
        }

    }

    @Override
    protected JsonObject getClientCapabilities() {
        return new JsonObject().put("roots", Map.of());
    }

}
