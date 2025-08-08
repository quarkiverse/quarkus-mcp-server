package io.quarkiverse.mcp.server.test.roots;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Notification;
import io.quarkiverse.mcp.server.Notification.Type;
import io.quarkiverse.mcp.server.Roots;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.TimeoutException;

public class RootsStreamableErrorTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.roots.default-timeout", "2s");

    @Test
    public void testRoots() throws InterruptedException {
        McpAssured.newStreamableClient()
                .setOpenSubsidiarySse(false)
                .setClientCapabilities(new ClientCapability(ClientCapability.ROOTS, Map.of()))
                .build()
                .connect();
        assertTrue(MyTools.INIT_LATCH.await(5, TimeUnit.SECONDS));
    }

    @Singleton
    public static class MyTools {

        static final CountDownLatch INIT_LATCH = new CountDownLatch(1);

        @Notification(Type.INITIALIZED)
        void init(McpConnection connection, Roots roots) {
            if (roots.isSupported()) {
                try {
                    roots.listAndAwait();
                    throw new AssertionError();
                } catch (TimeoutException expected) {
                }
            }
            INIT_LATCH.countDown();
        }

    }
}
