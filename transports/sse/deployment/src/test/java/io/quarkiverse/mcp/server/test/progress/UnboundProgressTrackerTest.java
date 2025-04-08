package io.quarkiverse.mcp.server.test.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.ProgressTracker;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

public class UnboundProgressTrackerTest extends ProgressTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    protected void assertProgressNotification(JsonObject notification, String token, int progress, double total,
            String message) {
        JsonObject params = notification.getJsonObject("params");
        assertEquals(token, params.getString("progressToken"));
        assertEquals(progress, params.getInteger("progress"));
    }

    @Singleton
    public static class MyTools {

        private final ExecutorService executor;

        MyTools() {
            this.executor = Executors.newFixedThreadPool(1);
        }

        @PreDestroy
        void destroy() {
            executor.shutdownNow();
        }

        @Tool
        Uni<String> longRunning(Progress progress) {
            if (progress.token().isEmpty()) {
                return Uni.createFrom().item("nok");
            }
            // Unbound tracker with default step = 1
            ProgressTracker tracker = progress.trackerBuilder().build();

            CompletableFuture<String> ret = new CompletableFuture<String>();
            executor.execute(() -> {
                for (int i = 0; i < 10; i++) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    tracker.advance();
                }
                ret.complete("ok");
            });
            return Uni.createFrom().completionStage(ret);
        }

    }

}
