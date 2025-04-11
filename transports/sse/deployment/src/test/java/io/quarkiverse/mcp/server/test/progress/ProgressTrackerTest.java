package io.quarkiverse.mcp.server.test.progress;

import java.math.BigDecimal;
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

public class ProgressTrackerTest extends ProgressTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

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
            ProgressTracker tracker = progress.trackerBuilder()
                    .setDefaultStep(1)
                    .setTotal(10.2)
                    .setMessageBuilder(i -> "Long running progress: " + i)
                    .build();

            CompletableFuture<String> ret = new CompletableFuture<String>();
            executor.execute(() -> {
                for (int i = 0; i < 10; i++) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    try {
                        tracker.advance(1000);
                        throw new AssertionError();
                    } catch (IllegalStateException expected) {
                    }
                    CompletableFuture<Void> cf = tracker.advanceAsync(BigDecimal.valueOf(1000))
                            .subscribeAsCompletionStage();
                    if (!cf.isCompletedExceptionally()) {
                        throw new AssertionError();
                    }
                    tracker.advance();
                }
                ret.complete("ok");
            });
            return Uni.createFrom().completionStage(ret);
        }

    }

}
