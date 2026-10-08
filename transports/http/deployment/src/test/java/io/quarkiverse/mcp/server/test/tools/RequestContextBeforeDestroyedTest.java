package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class RequestContextBeforeDestroyedTest extends McpServerTest {

    static final String VALUE = "tool request";

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(Features.class, RequestBean.class, LifecycleObserver.class));

    @Test
    public void testBeforeDestroyedUsesToolRequestContext() throws InterruptedException {
        try (var client = McpAssured.newConnectedStreamableClient()) {
            client.when()
                    .toolsCall("touch", r -> assertEquals(VALUE, r.firstContent().asText().text()))
                    .thenAssertResults();

            assertTrue(LifecycleObserver.OBSERVED.await(10, TimeUnit.SECONDS),
                    "The request context lifecycle observer was not called");
            // Arc logs observer exceptions rather than propagating them to the request, so capture
            // failures explicitly instead of relying on assertions inside the observer.
            assertNull(LifecycleObserver.FAILURE.get(),
                    "The observer could not access the request scoped bean");
            assertEquals(VALUE, LifecycleObserver.VALUE.get(),
                    "The observer must see the bean instance used by the tool call");
        }
    }

    public static class Features {

        @Inject
        RequestBean bean;

        @Tool
        String touch() {
            bean.setValue(VALUE);
            // Ignore lifecycle events from initialization; observe the next context destruction
            // after the tool has populated its request scoped bean.
            LifecycleObserver.ARMED.set(true);
            return bean.getValue();
        }
    }

    @RequestScoped
    public static class RequestBean {

        private volatile String value;

        void setValue(String value) {
            this.value = value;
        }

        String getValue() {
            return value;
        }
    }

    @ApplicationScoped
    public static class LifecycleObserver {

        static final AtomicBoolean ARMED = new AtomicBoolean();
        static final CountDownLatch OBSERVED = new CountDownLatch(1);
        static final AtomicReference<String> VALUE = new AtomicReference<>();
        static final AtomicReference<Exception> FAILURE = new AtomicReference<>();

        @Inject
        RequestBean bean;

        void beforeDestroyed(@Observes @BeforeDestroyed(RequestScoped.class) Object event) {
            if (ARMED.compareAndSet(true, false)) {
                try {
                    VALUE.set(bean.getValue());
                } catch (Exception e) {
                    FAILURE.set(e);
                } finally {
                    OBSERVED.countDown();
                }
            }
        }
    }
}
