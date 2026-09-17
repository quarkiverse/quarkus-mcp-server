package io.quarkiverse.mcp.server.test.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpResultException;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpAssert;
import io.quarkiverse.mcp.server.test.McpAssured.McpTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

/**
 * Proves that the existing {@link McpResultException} seam is enough for an extension (such as Tasks) to return a custom
 * {@code resultType} from a {@code tools/call} - no extra core code is required. Here a {@code @Tool} throws a task-like
 * result exception that yields {@code resultType: "task"}.
 */
public class TaskResultExceptionTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(TaskTools.class));

    @Test
    public void testSse() {
        assertTaskResult(McpAssured.newConnectedSseClient());
    }

    @Test
    public void testStreamable() {
        assertTaskResult(McpAssured.newConnectedStreamableClient());
    }

    @Test
    public void testStreamableStateless() {
        assertTaskResult(McpAssured.newStreamableClient().setStateless().build().connect());
    }

    static <A extends McpAssert<A>> void assertTaskResult(McpTestClient<A, ?> client) {
        try (client) {
            client.when()
                    .toolsCall("startTask")
                    // A non-standard result (resultType: "task") is asserted via the raw JSON-RPC response
                    .withRawAssert(response -> {
                        JsonObject result = response.getJsonObject("result");
                        assertNotNull(result);
                        // The custom resultType supplied by the exception is preserved (not overwritten with "complete")
                        assertEquals("task", result.getString("resultType"));
                        JsonObject task = result.getJsonObject("task");
                        assertNotNull(task);
                        assertEquals("task-123", task.getString("taskId"));
                        assertEquals("working", task.getString("status"));
                    })
                    .send()
                    .thenAssertResults();
        }
    }

    public static class TaskTools {

        @Tool(description = "Starts a long-running task and returns a task handle")
        String startTask() {
            throw new CreateTaskException("task-123");
        }
    }

    // A test-only extension exception - it reuses the McpResultException seam without any core changes
    static final class CreateTaskException extends McpResultException {

        private static final long serialVersionUID = 1L;

        private final String taskId;

        CreateTaskException(String taskId) {
            super("Task created: " + taskId);
            this.taskId = taskId;
        }

        @Override
        public JsonObject result() {
            return new JsonObject()
                    .put("resultType", "task")
                    .put("task", new JsonObject()
                            .put("taskId", taskId)
                            .put("status", "working"));
        }
    }

}
