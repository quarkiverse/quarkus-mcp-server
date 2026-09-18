package io.quarkiverse.mcp.server.tasks.test;

import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.TASKS_CAPABILITY;
import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.callToolAsTask;
import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.getTask;
import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.sleep;
import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.taskRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.tasks.Task;
import io.quarkiverse.mcp.server.tasks.TaskManager;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class TaskExpiredTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(MyTools.class, TaskTestSupport.class));

    @Inject
    TaskManager taskManager;

    @Test
    public void testExpiredTask() {
        try (var client = McpAssured.newStreamableClient()
                .setStateless()
                .setClientCapabilities(TASKS_CAPABILITY)
                .build()
                .connect()) {
            String taskId = callToolAsTask(client, "shortLived");
            JsonObject task = getTask(client, taskId, true);
            assertEquals(200L, task.getLong("ttlMs"));
            assertEquals(50L, task.getLong("pollIntervalMs"));

            sleep(400);

            client.when()
                    .message(taskRequest(client, "tasks/get", taskId, true))
                    .withErrorAssert(e -> {
                        assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, e.code());
                        assertTrue(e.message().endsWith("Task has expired"), e.message());
                    })
                    .send()
                    .thenAssertResults();
            // The expired task was removed
            assertNull(taskManager.getTask(taskId));

            // An unlimited TTL is advertised as null
            taskId = callToolAsTask(client, "unlimited");
            task = getTask(client, taskId, true);
            assertTrue(task.containsKey("ttlMs"));
            assertNull(task.getValue("ttlMs"));
        }
    }

    public static class MyTools {

        @Task(ttl = "200ms", pollInterval = "50ms")
        @Tool(description = "A short-lived task")
        String shortLived() {
            return "ok";
        }

        @Task(ttl = "0")
        @Tool(description = "A task with unlimited TTL")
        String unlimited() {
            return "ok";
        }

    }

}
