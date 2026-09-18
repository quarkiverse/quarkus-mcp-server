package io.quarkiverse.mcp.server.test.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.TaskManager;
import io.quarkiverse.mcp.server.TaskStatus;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpTestClient;
import io.vertx.core.json.JsonObject;

/**
 * Shared helpers for the MCP Tasks extension tests.
 */
final class TaskTestSupport {

    private TaskTestSupport() {
    }

    /**
     * The client capability declaring the tasks extension.
     */
    static final ClientCapability TASKS_CAPABILITY = new ClientCapability(ClientCapability.EXTENSIONS,
            Map.of(TaskManager.EXTENSION_ID, Map.of()));

    /**
     * @return the {@code io.modelcontextprotocol/clientCapabilities} object declaring the tasks extension
     */
    static JsonObject tasksClientCapabilities() {
        return new JsonObject().put(ClientCapability.EXTENSIONS,
                new JsonObject().put(TaskManager.EXTENSION_ID, new JsonObject()));
    }

    static JsonObject newRequest(McpTestClient<?, ?> client, String method, JsonObject params, boolean stateless) {
        JsonObject request = client.newRequest(method);
        if (params != null) {
            request.put("params", params);
        }
        if (stateless) {
            McpAssured.injectStatelessMeta(request, tasksClientCapabilities());
        }
        return request;
    }

    static JsonObject taskRequest(McpTestClient<?, ?> client, String method, String taskId, boolean stateless) {
        return newRequest(client, method, new JsonObject().put("taskId", taskId), stateless);
    }

    /**
     * Calls the tool and asserts that a {@code CreateTaskResult} is returned.
     *
     * @return the task id
     */
    static String callToolAsTask(McpTestClient<?, ?> client, String toolName) {
        return callToolAsTask(client, toolName, null);
    }

    static String callToolAsTask(McpTestClient<?, ?> client, String toolName, Map<String, Object> args) {
        AtomicReference<String> taskId = new AtomicReference<>();
        var message = client.when().toolsCall(toolName);
        if (args != null) {
            message = message.withArguments(args);
        }
        message.withRawAssert(response -> {
            JsonObject result = response.getJsonObject("result");
            assertNotNull(result);
            assertEquals("task", result.getString("resultType"));
            assertNotNull(result.getString("taskId"));
            assertEquals(TaskStatus.WORKING.jsonValue(), result.getString("status"));
            assertNotNull(result.getString("createdAt"));
            assertNotNull(result.getString("lastUpdatedAt"));
            assertNotNull(result.getLong("pollIntervalMs"));
            taskId.set(result.getString("taskId"));
        }).send().thenAssertResults();
        return taskId.get();
    }

    /**
     * @return the {@code tasks/get} result
     */
    static JsonObject getTask(McpTestClient<?, ?> client, String taskId, boolean stateless) {
        AtomicReference<JsonObject> ret = new AtomicReference<>();
        client.when()
                .message(taskRequest(client, "tasks/get", taskId, stateless))
                .withAssert(response -> {
                    JsonObject result = response.getJsonObject("result");
                    assertNotNull(result);
                    assertEquals("complete", result.getString("resultType"));
                    assertEquals(taskId, result.getString("taskId"));
                    ret.set(result);
                })
                .send()
                .thenAssertResults();
        return ret.get();
    }

    /**
     * Polls the task until it reaches the given status.
     *
     * @return the {@code tasks/get} result
     */
    static JsonObject awaitStatus(McpTestClient<?, ?> client, String taskId, boolean stateless, TaskStatus status) {
        long deadline = System.currentTimeMillis() + 10_000;
        JsonObject task = null;
        while (System.currentTimeMillis() < deadline) {
            task = getTask(client, taskId, stateless);
            String current = task.getString("status");
            if (status.jsonValue().equals(current)) {
                return task;
            }
            if (TaskStatus.from(current).isTerminal()) {
                fail("Task " + taskId + " reached the terminal status " + current + " instead of " + status.jsonValue()
                        + ": " + task.encodePrettily());
            }
            sleep(50);
        }
        fail("Task " + taskId + " did not reach the status " + status.jsonValue() + " in time: " + task);
        return null;
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

}
