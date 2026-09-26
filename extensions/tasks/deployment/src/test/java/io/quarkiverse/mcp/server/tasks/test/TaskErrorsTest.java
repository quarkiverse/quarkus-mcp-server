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
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.tasks.TaskManager;
import io.quarkiverse.mcp.server.tasks.TaskStatus;
import io.quarkiverse.mcp.server.tasks.Tasks;
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
        // A JSON-RPC error thrown by the handler -> failed
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
        // The tool decides to run synchronously if the client did not declare the capability
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

        // ...but creating a task without the capability fails with -32021
        client.when()
                .toolsCall("failing")
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

        @Tool(description = "Fails with a JSON-RPC error")
        String failing(Tasks tasks) {
            // Does not check isSupported() on purpose
            throw tasks.newTask().setHandler(task -> {
                throw new McpException("Invalid input", JsonRpcErrorCodes.INVALID_PARAMS);
            }, false).create();
        }

        @Tool(description = "Fails unexpectedly")
        String broken(Tasks tasks) {
            throw tasks.newTask().setHandler(task -> {
                throw new IllegalStateException("Boom");
            }, false).create();
        }

        @Tool(description = "Fails with a tool error")
        String toolError(Tasks tasks) {
            if (!tasks.isSupported()) {
                throw new ToolCallException("Business error");
            }
            throw tasks.newTask().setHandler(task -> {
                throw new ToolCallException("Business error");
            }, false).create();
        }

        @Tool(description = "Falls back to the synchronous execution")
        ToolResponse sync(Tasks tasks) {
            assertFalse(tasks.isSupported());
            try {
                tasks.newTask();
                throw new AssertionError("Not expected");
            } catch (McpException expected) {
                assertEquals(JsonRpcErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY, expected.getJsonRpcErrorCode());
            }
            return ToolResponse.success("not a task");
        }

    }

}
