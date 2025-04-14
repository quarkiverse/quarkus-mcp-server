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

import io.quarkiverse.mcp.server.InitManager;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.runtime.Startup;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class InitProgrammaticTest extends McpServerTest {

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

        @Inject
        InitManager initManager;

        @Startup
        void starup() {
            initManager.newInit("foo").setHandler(args -> {
                if (args.connection().initialRequest().supportsSampling()) {
                    initCalled.set(true);
                }
                INIT_LATCH.countDown();
                return null;
            }).register();
        }

    }

    @Override
    protected JsonObject getClientCapabilities() {
        return new JsonObject().put("sampling", Map.of());
    }

}
