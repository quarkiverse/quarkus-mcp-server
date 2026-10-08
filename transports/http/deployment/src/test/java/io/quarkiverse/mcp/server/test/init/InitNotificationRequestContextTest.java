package io.quarkiverse.mcp.server.test.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Notification;
import io.quarkiverse.mcp.server.Notification.Type;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableContext.ContextState;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;

public class InitNotificationRequestContextTest extends McpServerTest {

    static final CountDownLatch STARTED = new CountDownLatch(2);
    static final CountDownLatch DESTROYED = new CountDownLatch(2);
    static final CompletableFuture<Void> RELEASE = new CompletableFuture<>();
    static final Set<ContextState> NOTIFICATION_CONTEXTS = ConcurrentHashMap.newKeySet();
    static final ConcurrentLinkedQueue<String> DESTROYED_VALUES = new ConcurrentLinkedQueue<>();
    static final ConcurrentLinkedQueue<String> OBSERVED_VALUES = new ConcurrentLinkedQueue<>();
    static final ConcurrentLinkedQueue<RuntimeException> OBSERVER_FAILURES = new ConcurrentLinkedQueue<>();

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(Notifications.class, RequestBean.class, LifecycleObserver.class));

    @Test
    public void testOverlappingNotificationsOwnSeparateContexts() throws InterruptedException {
        try (var client = McpAssured.newConnectedStreamableClient()) {
            try {
                assertTrue(STARTED.await(10, TimeUnit.SECONDS),
                        "Both initialized notifications must start before either completes");
            } finally {
                RELEASE.complete(null);
            }

            assertTrue(DESTROYED.await(10, TimeUnit.SECONDS),
                    "Both initialized notifications must destroy their request scoped beans");
            assertEquals(List.of("first", "second"), DESTROYED_VALUES.stream().sorted().toList());
            assertTrue(OBSERVER_FAILURES.isEmpty(), () -> "Lifecycle observer failures: " + OBSERVER_FAILURES);
            assertEquals(List.of("first", "second"), OBSERVED_VALUES.stream().sorted().toList(),
                    "Each observer must access the bean belonging to the notification being destroyed");
        }
    }

    public static class Notifications {

        @Inject
        RequestBean bean;

        @Notification(Type.INITIALIZED)
        Uni<Void> first() {
            return started("first");
        }

        @Notification(Type.INITIALIZED)
        Uni<Void> second() {
            return started("second");
        }

        private Uni<Void> started(String value) {
            bean.setValue(value);
            NOTIFICATION_CONTEXTS.add(Arc.container().requestContext().getState());
            STARTED.countDown();
            return Uni.createFrom().completionStage(RELEASE);
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

        @PreDestroy
        void destroy() {
            DESTROYED_VALUES.add(value);
            DESTROYED.countDown();
        }
    }

    @ApplicationScoped
    public static class LifecycleObserver {

        @Inject
        RequestBean bean;

        void beforeDestroyed(@Observes @BeforeDestroyed(RequestScoped.class) Object event) {
            // Observe only notification contexts, excluding HTTP handshake lifecycle events.
            var requestContext = Arc.container().requestContext();
            if (!requestContext.isActive()
                    || !NOTIFICATION_CONTEXTS.contains(requestContext.getState())) {
                return;
            }
            try {
                OBSERVED_VALUES.add(bean.getValue());
            } catch (RuntimeException e) {
                // Arc logs observer failures, so expose them explicitly to the test.
                OBSERVER_FAILURES.add(e);
            }
        }
    }
}
