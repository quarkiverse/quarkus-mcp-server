package io.quarkiverse.mcp.server.test.tasks;

import static io.quarkiverse.mcp.server.test.tasks.TaskTestSupport.TASKS_CAPABILITY;
import static io.quarkiverse.mcp.server.test.tasks.TaskTestSupport.awaitStatus;
import static io.quarkiverse.mcp.server.test.tasks.TaskTestSupport.callToolAsTask;
import static io.quarkiverse.mcp.server.test.tasks.TaskTestSupport.getTask;
import static io.quarkiverse.mcp.server.test.tasks.TaskTestSupport.taskRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.Task;
import io.quarkiverse.mcp.server.TaskContext;
import io.quarkiverse.mcp.server.TaskManager;
import io.quarkiverse.mcp.server.TaskStatus;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpAssert;
import io.quarkiverse.mcp.server.test.McpAssured.McpTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class TaskCancelTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .overrideConfigKey("quarkus.mcp.server.tasks.default-poll-interval", "100ms")
            .withApplicationRoot(root -> root.addClasses(MyTools.class, TaskTestSupport.class));

    @Inject
    TaskManager taskManager;

    @Test
    public void testStreamableStateless() {
        try (var client = McpAssured.newStreamableClient()
                .setStateless()
                .setClientCapabilities(TASKS_CAPABILITY)
                .build()
                .connect()) {
            assertCancel(client, true);
        }
    }

    @Test
    public void testSse() {
        try (var client = McpAssured.newSseClient()
                .setClientCapabilities(TASKS_CAPABILITY)
                .build()
                .connect()) {
            assertCancel(client, false);
        }
    }

    <A extends McpAssert<A>> void assertCancel(McpTestClient<A, ?> client, boolean stateless) {
        MyTools.reset();
        String taskId = callToolAsTask(client, "cancellable");
        assertEquals(TaskStatus.WORKING.jsonValue(), getTask(client, taskId, stateless).getString("status"));

        // tasks/cancel is acknowledged with an empty result
        client.when()
                .message(taskRequest(client, "tasks/cancel", taskId, stateless))
                .withAssert(response -> {
                    JsonObject result = response.getJsonObject("result");
                    assertNotNull(result);
                    assertEquals("complete", result.getString("resultType"));
                })
                .send()
                .thenAssertResults();

        JsonObject cancelled = awaitStatus(client, taskId, stateless, TaskStatus.CANCELLED);
        assertEquals("Cancellation requested by the client", cancelled.getString("statusMessage"));

        // The tool observes the cancellation
        try {
            assertTrue(MyTools.CANCELLED.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        assertEquals(Optional.of("Cancellation requested by the client"), MyTools.REASON.get());
        // The tool completed after the cancellation - the result is discarded
        try {
            assertTrue(MyTools.FINISHED.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        assertEquals(TaskStatus.CANCELLED.jsonValue(), getTask(client, taskId, stateless).getString("status"));
        assertFalse(getTask(client, taskId, stateless).containsKey("result"));

        // Cancelling a terminal task is still acknowledged
        client.when()
                .message(taskRequest(client, "tasks/cancel", taskId, stateless))
                .withAssert(response -> assertNotNull(response.getJsonObject("result")))
                .send()
                .thenAssertResults();
    }

    @Test
    public void testCancelViaTaskManager() {
        try (var client = McpAssured.newStreamableClient()
                .setStateless()
                .setClientCapabilities(TASKS_CAPABILITY)
                .build()
                .connect()) {
            MyTools.reset();
            String taskId = callToolAsTask(client, "cancellable");
            assertTrue(taskManager.cancelTask(taskId, "Enough"));
            assertFalse(taskManager.cancelTask(taskId, "Enough"));
            assertFalse(taskManager.cancelTask("unknown", null));
            JsonObject cancelled = awaitStatus(client, taskId, true, TaskStatus.CANCELLED);
            assertEquals("Enough", cancelled.getString("statusMessage"));
            assertEquals(TaskStatus.CANCELLED, taskManager.getTask(taskId).status());
        }
    }

    public static class MyTools {

        static volatile CountDownLatch CANCELLED;
        static volatile CountDownLatch FINISHED;
        static final AtomicReference<Optional<String>> REASON = new AtomicReference<>();

        static void reset() {
            CANCELLED = new CountDownLatch(1);
            FINISHED = new CountDownLatch(1);
            REASON.set(null);
        }

        @Task
        @Tool(description = "A cancellable tool")
        String cancellable(TaskContext task, Cancellation cancellation) throws InterruptedException {
            cancellation.onCancelled(reason -> {
                REASON.set(reason);
                CANCELLED.countDown();
            });
            long deadline = System.currentTimeMillis() + 10_000;
            while (!cancellation.check().isRequested() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            FINISHED.countDown();
            return "finished";
        }

    }

}
