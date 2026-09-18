package io.quarkiverse.mcp.server.test.tasks;

import static io.quarkiverse.mcp.server.test.tasks.TaskTestSupport.TASKS_CAPABILITY;
import static io.quarkiverse.mcp.server.test.tasks.TaskTestSupport.callToolAsTask;
import static io.quarkiverse.mcp.server.test.tasks.TaskTestSupport.tasksClientCapabilities;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.Task;
import io.quarkiverse.mcp.server.TaskContext;
import io.quarkiverse.mcp.server.TaskStatus;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class TaskNotificationsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(MyTools.class, TaskTestSupport.class));

    @Test
    public void testTaskStatusNotifications() throws InterruptedException {
        MyTools.LATCH = new CountDownLatch(1);
        try (McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .setClientCapabilities(TASKS_CAPABILITY)
                .build()
                .connect()) {
            String taskId = callToolAsTask(client, "slow");

            // Subscribe to the task status notifications; an unknown task id is not acknowledged
            JsonObject listenRequest = client.newRequest("subscriptions/listen");
            listenRequest.put("params", new JsonObject()
                    .put("notifications", new JsonObject()
                            .put("taskIds", new JsonArray().add(taskId).add("unknown"))));
            McpAssured.injectStatelessMeta(listenRequest, tasksClientCapabilities());
            client.sendAndForget(listenRequest);

            List<JsonObject> notifications = client.waitForNotifications(1).notifications();
            JsonObject ack = notifications.get(0);
            assertEquals("notifications/subscriptions/acknowledged", ack.getString("method"));
            assertEquals(new JsonArray().add(taskId),
                    ack.getJsonObject("params").getJsonObject("notifications").getJsonArray("taskIds"));

            // Let the tool proceed - the status message update and the completion are pushed
            MyTools.LATCH.countDown();
            notifications = client.waitForNotifications(3).notifications();

            JsonObject working = notifications.get(1);
            assertEquals("notifications/tasks", working.getString("method"));
            JsonObject params = working.getJsonObject("params");
            assertEquals(taskId, params.getString("taskId"));
            assertEquals(TaskStatus.WORKING.jsonValue(), params.getString("status"));
            assertEquals("Almost done", params.getString("statusMessage"));
            assertEquals(listenRequest.getValue("id"),
                    params.getJsonObject("_meta").getValue("io.modelcontextprotocol/subscriptionId"));

            JsonObject completed = notifications.get(2);
            assertEquals("notifications/tasks", completed.getString("method"));
            params = completed.getJsonObject("params");
            assertEquals(taskId, params.getString("taskId"));
            assertEquals(TaskStatus.COMPLETED.jsonValue(), params.getString("status"));
            JsonObject result = params.getJsonObject("result");
            assertNotNull(result);
            assertEquals("done", result.getJsonArray("content").getJsonObject(0).getString("text"));
        }
    }

    @Test
    public void testMissingCapability() {
        try (McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect()) {
            JsonObject listenRequest = client.newRequest("subscriptions/listen");
            listenRequest.put("params", new JsonObject()
                    .put("notifications", new JsonObject()
                            .put("taskIds", new JsonArray().add("foo"))));
            McpAssured.injectStatelessMeta(listenRequest);
            client.when()
                    .message(listenRequest)
                    .withErrorAssert(error -> {
                        assertEquals(JsonRpcErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY, error.code());
                        assertTrue(error.data().getJsonObject("requiredCapabilities").getJsonObject("extensions")
                                .containsKey("io.modelcontextprotocol/tasks"));
                    })
                    .send()
                    .thenAssertResults();
        }
    }

    public static class MyTools {

        static volatile CountDownLatch LATCH;

        @Task
        @Tool(description = "A slow tool")
        String slow(TaskContext task) throws InterruptedException {
            if (!LATCH.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Latch not released");
            }
            task.setStatusMessage("Almost done");
            return "done";
        }

    }

}
