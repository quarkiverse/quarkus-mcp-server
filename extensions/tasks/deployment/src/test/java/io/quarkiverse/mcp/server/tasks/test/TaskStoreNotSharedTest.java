package io.quarkiverse.mcp.server.tasks.test;

import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.TASKS_CAPABILITY;
import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.awaitStatus;
import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.callToolAsTask;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.tasks.TaskStatus;
import io.quarkiverse.mcp.server.tasks.Tasks;
import io.quarkiverse.mcp.server.tasks.runtime.InMemoryTaskStore;
import io.quarkiverse.mcp.server.tasks.spi.TaskRecord;
import io.quarkiverse.mcp.server.tasks.spi.TaskStore;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;

/**
 * Tests that the running tasks are not checked periodically if the {@link TaskStore} is not shared.
 */
public class TaskStoreNotSharedTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .overrideConfigKey("quarkus.mcp.server.tasks.default-poll-interval", "50ms")
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, CountingInMemoryTaskStore.class, TaskTestSupport.class));

    @Inject
    CountingInMemoryTaskStore store;

    @Test
    public void testNoPolling() throws InterruptedException {
        assertFalse(store.isShared());
        try (var client = newClient()) {
            String taskId = callToolAsTask(client, "blocking");
            assertTrue(MyTools.STARTED.await(5, TimeUnit.SECONDS));
            // Wait for several poll intervals
            Thread.sleep(500);
            assertEquals(0, store.getAllCalls.get());

            MyTools.RELEASE.countDown();
            awaitStatus(client, taskId, true, TaskStatus.COMPLETED);
            assertEquals(0, store.getAllCalls.get());
        }
    }

    private McpStreamableTestClient newClient() {
        return McpAssured.newStreamableClient()
                .setStateless()
                .setAdditionalHeaders(TaskTestSupport::taskHeaders)
                .setClientCapabilities(TASKS_CAPABILITY)
                .build()
                .connect();
    }

    @Singleton
    public static class CountingInMemoryTaskStore extends InMemoryTaskStore {

        final AtomicInteger getAllCalls = new AtomicInteger();

        @Override
        public Uni<Iterable<TaskRecord>> getAll(Set<String> taskIds) {
            getAllCalls.incrementAndGet();
            return super.getAll(taskIds);
        }

    }

    public static class MyTools {

        static final CountDownLatch STARTED = new CountDownLatch(1);
        static final CountDownLatch RELEASE = new CountDownLatch(1);

        @Tool(description = "A blocking task")
        String blocking(Tasks tasks) {
            throw tasks.newTask().setHandler(task -> {
                STARTED.countDown();
                try {
                    if (!RELEASE.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Not released");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return ToolResponse.success("done");
            }, false).createAndAwait();
        }

    }

}
