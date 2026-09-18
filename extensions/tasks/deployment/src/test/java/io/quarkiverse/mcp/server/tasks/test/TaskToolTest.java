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

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.tasks.Task;
import io.quarkiverse.mcp.server.tasks.TaskContext;
import io.quarkiverse.mcp.server.tasks.TaskManager;
import io.quarkiverse.mcp.server.tasks.TaskOptions;
import io.quarkiverse.mcp.server.tasks.TaskStatus;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.InitResult;
import io.quarkiverse.mcp.server.test.McpAssured.McpAssert;
import io.quarkiverse.mcp.server.test.McpAssured.McpTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ServerCapability;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class TaskToolTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .overrideConfigKey("quarkus.mcp.server.tasks.default-poll-interval", "100ms")
            .withApplicationRoot(root -> root.addClasses(MyTools.class, TaskTestSupport.class));

    @Inject
    ToolManager toolManager;

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
    public void testProgrammaticTool() {
        // The task options are registered before the tool so that the tool is task-augmented as soon as it is available
        taskManager.setTaskOptions("dynamicTask", McpServer.DEFAULT,
                new TaskOptions(false, Duration.ofMinutes(5), Duration.ofMillis(200)));
        assertTrue(taskManager.getTaskOptions("dynamicTask", McpServer.DEFAULT).isPresent());
        toolManager.newTool("dynamicTask")
                .setDescription("A programmatically registered task-augmented tool")
                .setHandler(args -> {
                    TaskContext task = args.custom(TaskContext.class);
                    task.setStatusMessage("Working hard");
                    return ToolResponse.success(new TextContent("dynamic:" + task.id()));
                }, false)
                .register();
        try {
            try (var client = McpAssured.newStreamableClient()
                    .setStateless()
                    .setClientCapabilities(TASKS_CAPABILITY)
                    .build()
                    .connect()) {
                String taskId = callToolAsTask(client, "dynamicTask");
                JsonObject task = awaitStatus(client, taskId, true, TaskStatus.COMPLETED);
                assertEquals(300_000L, task.getLong("ttlMs"));
                assertEquals(200L, task.getLong("pollIntervalMs"));
                assertEquals("Working hard", task.getString("statusMessage"));
                assertEquals("dynamic:" + taskId,
                        task.getJsonObject("result").getJsonArray("content").getJsonObject(0).getString("text"));
            }
            // The tool is executed synchronously for a client without the capability
            try (var client = McpAssured.newConnectedStreamableClient()) {
                client.when()
                        .toolsCall("dynamicTask")
                        .withAssert(r -> {
                            assertFalse(r.isError());
                            assertEquals("dynamic:null", r.firstContent().asText().text());
                        })
                        .send()
                        .thenAssertResults();
            }
        } finally {
            toolManager.removeTool("dynamicTask");
            taskManager.setTaskOptions("dynamicTask", McpServer.DEFAULT, null);
            assertTrue(taskManager.getTaskOptions("dynamicTask", McpServer.DEFAULT).isEmpty());
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
        MyTools.OBSERVED_STATUS_MESSAGE = null;

        // A regular tool is executed synchronously
        client.when()
                .toolsCall("echo")
                .withArguments(Map.of("message", "hi"))
                .withAssert(r -> assertEquals("hi", r.firstContent().asText().text()))
                .send()
                .thenAssertResults();

        // A task-augmented tool returns a CreateTaskResult
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

        // Release the tool
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

        // The statusMessage may be set for a working task
        assertEquals("Working: 42", MyTools.OBSERVED_STATUS_MESSAGE);
    }

    public static class MyTools {

        static volatile CountDownLatch LATCH = new CountDownLatch(1);

        static volatile String OBSERVED_STATUS_MESSAGE;

        @Tool(description = "Echo")
        String echo(String message) {
            return message;
        }

        @Task(ttl = "10m")
        @Tool(description = "A long-running tool")
        String longRunning(@ToolArg(description = "Value") int value, TaskContext task) throws InterruptedException {
            assertTrue(task.isTaskAugmented());
            assertEquals(TaskStatus.WORKING, task.status());
            task.setStatusMessage("Working: " + value);
            OBSERVED_STATUS_MESSAGE = task.statusMessage();
            if (!LATCH.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Latch not released");
            }
            task.setStatusMessage("Finished");
            return "done:" + value + ":" + task.id();
        }

    }

}
