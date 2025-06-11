package io.quarkiverse.mcp.server.test.init;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.InitialCheck;
import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.Notification;
import io.quarkiverse.mcp.server.Notification.Type;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.vertx.VertxContextSupport;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;

public class InitCheckBlockingSuccessTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyInitCheck.class));

    @Test
    public void testInitRequest() throws InterruptedException {
        initClient();
        assertTrue(MyInitCheck.INIT_LATCH.await(50, TimeUnit.SECONDS));
        assertTrue(MyInitCheck.APPLIED.get());
    }

    @Singleton
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
            return VertxContextSupport.executeBlocking(new Callable<CheckResult>() {

                @Override
                public CheckResult call() throws Exception {
                    APPLIED.set(true);
                    if (!"foo".equals(request.getHeader("Mcp-Test"))) {
                        return new CheckResult(true, "Mcp-Test header not set");
                    }
                    return initialRequest.supportsSampling() ? InitialCheck.CheckResult.SUCCESS
                            : new CheckResult(true, "Sampling not supported");
                }
            });
        }
    }

    @Override
    protected Map<String, Object> defaultHeaders() {
        return Map.of("Mcp-Test", "foo");
    }

    @Override
    protected JsonObject getClientCapabilities() {
        return new JsonObject().put("sampling", Map.of());
    }

}
