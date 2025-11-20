package io.quarkiverse.mcp.server.test.progress;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;

public class ProgressNotificationTest extends ProgressTest {

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
            CompletableFuture<String> ret = new CompletableFuture<String>();
            executor.execute(() -> {
                for (int i = 1; i <= 10; i++) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    progress.notificationBuilder()
                            .setProgress(i)
                            .setTotal(10.2)
                            .setMessage("Long running progress: " + i)
                            .build()
                            .sendAndForget();
                }
                ret.complete("ok");
            });
            return Uni.createFrom().completionStage(ret);
        }

    }

}
