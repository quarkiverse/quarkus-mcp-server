package io.quarkiverse.mcp.server.test.init;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Notification;
import io.quarkiverse.mcp.server.Notification.Type;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

public class InitUniTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Inject
    MyTools myTools;

    @Test
    public void testInitRequest() throws URISyntaxException, InterruptedException {
        initClient();
        assertTrue(MyTools.INIT_LATCH.await(5, TimeUnit.SECONDS));
        assertTrue(myTools.initCalled.get());
    }

    @Singleton
    public static class MyTools {

        static final CountDownLatch INIT_LATCH = new CountDownLatch(1);
        AtomicBoolean initCalled = new AtomicBoolean();

        @Notification(Type.INITIALIZED)
        Uni<Void> onInit(McpConnection connection) {
            if (connection.initialRequest().supportsSampling()) {
                initCalled.set(true);
            }
            INIT_LATCH.countDown();
            return Uni.createFrom().voidItem();
        }

    }

    @Override
    protected JsonObject getClientCapabilities() {
        return new JsonObject().put("sampling", Map.of());
    }

}
