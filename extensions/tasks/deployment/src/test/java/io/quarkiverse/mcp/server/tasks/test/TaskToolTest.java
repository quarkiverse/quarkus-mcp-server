package io.quarkiverse.mcp.server.tasks.test;

import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.TASKS_CAPABILITY;
import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.awaitStatus;
import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.callToolAsTask;
import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.getTask;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.tasks.TaskManager;
import io.quarkiverse.mcp.server.tasks.TaskStatus;
import io.quarkiverse.mcp.server.tasks.Tasks;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.InitResult;
import io.quarkiverse.mcp.server.test.McpAssured.McpAssert;
import io.quarkiverse.mcp.server.test.McpAssured.McpTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ServerCapability;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

public class TaskToolTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .overrideConfigKey("quarkus.mcp.server.tasks.default-poll-interval", "100ms")
            .withApplicationRoot(root -> root.addClasses(MyTools.class, TaskTestSupport.class));

    @Inject
    TaskManager taskManager;

    @Test
    public void testSse() {
        try (var client = McpAssured.newSseClient()
                .setClientCapabilities(TASKS_CAPABILITY)
                .build()
                .connect(TaskToolTest::assertTasksAdvertised)) {
            assertTaskTool(client, false);
        }
    }

    @Test
    public void testStreamable() {
        try (var client = McpAssured.newStreamableClient()
                .setClientCapabilities(TASKS_CAPABILITY)
                .build()
                .connect(TaskToolTest::assertTasksAdvertised)) {
            assertTaskTool(client, false);
        }
    }

    @Test
    public void testStreamableStateless() {
        try (var client = McpAssured.newStreamableClient()
                .setStateless()
                .setClientCapabilities(TASKS_CAPABILITY)
                .build()
                .connect(TaskToolTest::assertTasksAdvertised)) {
            assertTaskTool(client, true);
        }
    }

    @Test
    public void testAsyncAndVirtualThreadHandlers() {
        try (var client = McpAssured.newStreamableClient()
                .setStateless()
                .setClientCapabilities(TASKS_CAPABILITY)
                .build()
                .connect()) {
            // A non-blocking handler executed on the event loop
            String taskId = callToolAsTask(client, "asyncTask");
            JsonObject completed = awaitStatus(client, taskId, true, TaskStatus.COMPLETED);
            assertEquals("async:" + taskId,
                    completed.getJsonObject("result").getJsonArray("content").getJsonObject(0).getString("text"));
            assertEquals(300_000L, completed.getLong("ttlMs"));
            assertEquals(200L, completed.getLong("pollIntervalMs"));
            assertEquals("Initial", completed.getString("statusMessage"));

            // A blocking handler executed on a virtual thread; on JDKs without virtual threads (< 21) Quarkus falls back
            // to a regular worker thread
            boolean virtualThreadsSupported = Runtime.version().feature() >= 21;
            taskId = callToolAsTask(client, "virtualThreadTask");
            completed = awaitStatus(client, taskId, true, TaskStatus.COMPLETED);
            assertEquals("virtual:" + virtualThreadsSupported,
                    completed.getJsonObject("result").getJsonArray("content").getJsonObject(0).getString("text"));
        }
    }

    static void assertTasksAdvertised(InitResult initResult) {
        ServerCapability extensions = initResult.capabilities().stream()
                .filter(c -> c.name().equals("extensions"))
                .findFirst()
                .orElse(null);
        assertNotNull(extensions, "The extensions capability should be advertised");
        assertEquals(Map.of(), extensions.properties().get(TaskManager.EXTENSION_ID));
    }

    <A extends McpAssert<A>> void assertTaskTool(McpTestClient<A, ?> client, boolean stateless) {
        MyTools.LATCH = new CountDownLatch(1);

        // A regular tool is executed synchronously
        client.when()
                .toolsCall("echo")
                .withArguments(Map.of("message", "hi"))
                .withAssert(r -> assertEquals("hi", r.firstContent().asText().text()))
                .send()
                .thenAssertResults();

        // A tool that creates a task returns a CreateTaskResult
        String taskId = callToolAsTask(client, "longRunning", Map.of("value", 42));

        TaskManager.TaskInfo info = taskManager.getTask(taskId);
        assertNotNull(info);
        assertEquals("longRunning", info.toolName());
        assertEquals("<default>", info.serverName());
        assertEquals(Duration.ofMinutes(10), info.ttl().orElseThrow());
        assertEquals(Duration.ofMillis(100), info.pollInterval());

        // The task is working until the latch is released
        JsonObject working = getTask(client, taskId, stateless);
        assertEquals(TaskStatus.WORKING.jsonValue(), working.getString("status"));
        assertEquals(TaskStatus.WORKING, info.status());
        assertEquals(600_000L, working.getLong("ttlMs"));
        assertEquals(100L, working.getLong("pollIntervalMs"));
        assertNull(working.getJsonObject("result"));

        // Release the handler
        MyTools.LATCH.countDown();

        JsonObject completed = awaitStatus(client, taskId, stateless, TaskStatus.COMPLETED);
        assertEquals("Finished", completed.getString("statusMessage"));
        JsonObject result = completed.getJsonObject("result");
        assertNotNull(result);
        assertFalse(result.getBoolean("isError"));
        assertEquals("done:42:" + taskId,
                result.getJsonArray("content").getJsonObject(0).getString("text"));
        assertNotNull(completed.getString("createdAt"));
        assertNotNull(completed.getString("lastUpdatedAt"));
        assertEquals(TaskStatus.COMPLETED, taskManager.getTask(taskId).status());

        // The result can be retrieved repeatedly
        JsonObject again = getTask(client, taskId, stateless);
        assertEquals(TaskStatus.COMPLETED.jsonValue(), again.getString("status"));
        assertEquals(result, again.getJsonObject("result"));
    }

    public static class MyTools {

        static volatile CountDownLatch LATCH = new CountDownLatch(1);

        @Tool(description = "Echo")
        String echo(String message) {
            return message;
        }

        @Tool(description = "A long-running tool")
        String longRunning(@ToolArg(description = "Value") int value, Tasks tasks) {
            assertTrue(tasks.isSupported());
            throw tasks.newTask()
                    .setTtl(Duration.ofMinutes(10))
                    .setHandler(task -> {
                        assertEquals(TaskStatus.WORKING, task.status());
                        task.setStatusMessage("Working: " + value);
                        try {
                            if (!LATCH.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Latch not released");
                            }
                        } catch (InterruptedException e) {
                            throw new IllegalStateException(e);
                        }
                        task.setStatusMessage("Finished");
                        return ToolResponse.success("done:" + value + ":" + task.id());
                    }, false)
                    .create();
        }

        @Tool(description = "A non-blocking task")
        Uni<ToolResponse> asyncTask(Tasks tasks) {
            return tasks.newTask()
                    .setTtl(Duration.ofMinutes(5))
                    .setPollInterval(Duration.ofMillis(200))
                    .setStatusMessage("Initial")
                    .setAsyncHandler(task -> Uni.createFrom().item(ToolResponse.success("async:" + task.id())))
                    .createAsync();
        }

        @Tool(description = "A task executed on a virtual thread")
        ToolResponse virtualThreadTask(Tasks tasks) {
            throw tasks.newTask()
                    .setHandler(task -> ToolResponse.success(
                            new TextContent("virtual:" + Thread.currentThread().getClass().getName().contains("Virtual"))),
                            true)
                    .create();
        }

    }

}
