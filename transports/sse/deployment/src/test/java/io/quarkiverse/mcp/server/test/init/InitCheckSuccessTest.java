package io.quarkiverse.mcp.server.test.init;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.InitialCheck;
import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.Notification;
import io.quarkiverse.mcp.server.Notification.Type;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;

public class InitCheckSuccessTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyInitCheck.class));

    @Test
    public void testInitRequest() throws InterruptedException {
        McpAssured.newSseClient()
                .setAdditionalHeaders(m -> MultiMap.caseInsensitiveMultiMap().add("Mcp-Test", "foo"))
                .setClientCapabilities(new ClientCapability(ClientCapability.SAMPLING, Map.of()))
                .build()
                .connect();
        assertTrue(MyInitCheck.INIT_LATCH.await(50, TimeUnit.SECONDS));
        assertTrue(MyInitCheck.APPLIED.get());
    }

    // @Singleton should be added automatically
    public static class MyInitCheck implements InitialCheck {

        static final CountDownLatch INIT_LATCH = new CountDownLatch(1);
        static final AtomicBoolean APPLIED = new AtomicBoolean();

        @Inject
        HttpServerRequest request;

        @Notification(Type.INITIALIZED)
        void onInit() {
            INIT_LATCH.countDown();
        }

        @Override
        public Uni<CheckResult> perform(InitialRequest initialRequest) {
            APPLIED.set(true);
            if (!"foo".equals(request.getHeader("Mcp-Test"))) {
                return InitialCheck.CheckResult.error("Mcp-Test header not set");
            }
            return initialRequest.supportsSampling() ? InitialCheck.CheckResult.successs()
                    : InitialCheck.CheckResult.error("Sampling not supported");
        }

    }

}
