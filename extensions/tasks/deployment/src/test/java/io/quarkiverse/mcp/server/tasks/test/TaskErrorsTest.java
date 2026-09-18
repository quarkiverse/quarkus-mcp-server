package io.quarkiverse.mcp.server.tasks.test;

import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.TASKS_CAPABILITY;
import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.awaitStatus;
import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.callToolAsTask;
import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.taskRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.tasks.Task;
import io.quarkiverse.mcp.server.tasks.TaskContext;
import io.quarkiverse.mcp.server.tasks.TaskManager;
import io.quarkiverse.mcp.server.tasks.TaskStatus;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpAssert;
import io.quarkiverse.mcp.server.test.McpAssured.McpError;
import io.quarkiverse.mcp.server.test.McpAssured.McpTestClient;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class TaskErrorsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .overrideConfigKey("quarkus.mcp.server.tasks.default-poll-interval", "100ms")
            .withApplicationRoot(root -> root.addClasses(MyTools.class, TaskTestSupport.class));

    @Test
    public void testStreamableStateless() {
        try (var client = McpAssured.newStreamableClient()
                .setStateless()
                .setClientCapabilities(TASKS_CAPABILITY)
                .build()
                .connect()) {
            assertTaskErrors(client, true);
        }
        try (var client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect()) {
            assertNoCapability(client, true);
        }
    }

    @Test
    public void testStreamable() {
        try (var client = McpAssured.newStreamableClient()
                .setClientCapabilities(TASKS_CAPABILITY)
                .build()
                .connect()) {
            assertTaskErrors(client, false);
        }
        try (var client = McpAssured.newConnectedStreamableClient()) {
            assertNoCapability(client, false);
        }
    }

    <A extends McpAssert<A>> void assertTaskErrors(McpTestClient<A, ?> client, boolean stateless) {
        // A JSON-RPC error thrown by the tool -> failed
        String taskId = callToolAsTask(client, "failing");
        JsonObject failed = awaitStatus(client, taskId, stateless, TaskStatus.FAILED);
        JsonObject error = failed.getJsonObject("error");
        assertNotNull(error);
        assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, error.getInteger("code"));
        assertEquals("Invalid input", error.getString("message"));
        assertEquals("Invalid input", failed.getString("statusMessage"));
        assertNull(failed.getJsonObject("result"));

        // An unexpected exception -> failed with an internal error
        taskId = callToolAsTask(client, "broken");
        failed = awaitStatus(client, taskId, stateless, TaskStatus.FAILED);
        assertEquals(JsonRpcErrorCodes.INTERNAL_ERROR, failed.getJsonObject("error").getInteger("code"));

        // A tool error -> completed with isError: true
        taskId = callToolAsTask(client, "toolError");
        JsonObject completed = awaitStatus(client, taskId, stateless, TaskStatus.COMPLETED);
        JsonObject result = completed.getJsonObject("result");
        assertTrue(result.getBoolean("isError"));
        assertEquals("Business error", result.getJsonArray("content").getJsonObject(0).getString("text"));
        assertNull(completed.getJsonObject("error"));

        // Unknown task
        for (String method : new String[] { "tasks/get", "tasks/update", "tasks/cancel" }) {
            client.when()
                    .message(taskRequest(client, method, "unknown", stateless))
                    .withErrorAssert(e -> {
                        assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, e.code());
                        assertTrue(e.message().endsWith("Task not found"), e.message());
                    })
                    .send()
                    .thenAssertResults();
        }

        // Missing taskId; in the stateless mode the Mcp-Name header is required and validated by the transport
        if (!stateless) {
            client.when()
                    .message(TaskTestSupport.newRequest(client, "tasks/get", new JsonObject(), stateless))
                    .withErrorAssert(e -> {
                        assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, e.code());
                        assertTrue(e.message().contains("taskId"), e.message());
                    })
                    .send()
                    .thenAssertResults();
        }
    }

    <A extends McpAssert<A>> void assertNoCapability(McpTestClient<A, ?> client, boolean stateless) {
        // A task-augmented tool is executed synchronously if the client did not declare the capability
        client.when()
                .toolsCall("failing")
                .withErrorAssert(e -> assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, e.code()))
                .send()
                .thenAssertResults();
        client.when()
                .toolsCall("toolError")
                .withAssert(r -> {
                    assertTrue(r.isError());
                    assertEquals("Business error", r.firstContent().asText().text());
                })
                .send()
                .thenAssertResults();
        client.when()
                .toolsCall("sync")
                .withAssert(r -> {
                    assertFalse(r.isError());
                    assertEquals("not a task", r.firstContent().asText().text());
                })
                .send()
                .thenAssertResults();

        // ...unless the tool requires the capability
        client.when()
                .toolsCall("requiresTask")
                .withErrorAssert(TaskErrorsTest::assertMissingCapability)
                .send()
                .thenAssertResults();

        // The tasks/* methods require the capability as well
        for (String method : new String[] { "tasks/get", "tasks/update", "tasks/cancel" }) {
            JsonObject request = client.newRequest(method).put("params", new JsonObject().put("taskId", "foo"));
            if (stateless) {
                McpAssured.injectStatelessMeta(request);
            }
            client.when()
                    .message(request)
                    .withErrorAssert(TaskErrorsTest::assertMissingCapability)
                    .send()
                    .thenAssertResults();
        }
    }

    static void assertMissingCapability(McpError error) {
        assertEquals(JsonRpcErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY, error.code());
        assertNotNull(error.data());
        JsonObject required = error.data().getJsonObject("requiredCapabilities");
        assertNotNull(required);
        assertEquals(new JsonObject(), required.getJsonObject("extensions").getJsonObject(TaskManager.EXTENSION_ID));
    }

    public static class MyTools {

        @Task
        @Tool(description = "Fails with a JSON-RPC error")
        String failing() {
            throw new McpException("Invalid input", JsonRpcErrorCodes.INVALID_PARAMS);
        }

        @Task
        @Tool(description = "Fails unexpectedly")
        String broken() {
            throw new IllegalStateException("Boom");
        }

        @Task
        @Tool(description = "Fails with a tool error")
        String toolError() {
            throw new ToolCallException("Business error");
        }

        @Task
        @Tool(description = "Falls back to the synchronous execution")
        String sync(TaskContext task) {
            assertFalse(task.isTaskAugmented());
            assertNull(task.id());
            assertNull(task.status());
            // no-op
            task.setStatusMessage("ignored");
            try {
                task.inputRequestBuilder();
                throw new AssertionError("Not expected");
            } catch (IllegalStateException expected) {
            }
            return "not a task";
        }

        @Task(required = true)
        @Tool(description = "Requires the tasks capability")
        String requiresTask() {
            return "task";
        }

    }

}
