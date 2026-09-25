package io.quarkiverse.mcp.server.tasks.test;

import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.TASKS_CAPABILITY;
import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.awaitStatus;
import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.callToolAsTask;
import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.getTask;
import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.newRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationRequest.StringSchema;
import io.quarkiverse.mcp.server.ElicitationResponse;
import io.quarkiverse.mcp.server.InputResponses;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.tasks.TaskStatus;
import io.quarkiverse.mcp.server.tasks.Tasks;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpAssert;
import io.quarkiverse.mcp.server.test.McpAssured.McpTestClient;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class TaskInputRequiredTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .overrideConfigKey("quarkus.mcp.server.tasks.default-poll-interval", "100ms")
            .withApplicationRoot(root -> root.addClasses(MyTools.class, TaskTestSupport.class));

    static final ClientCapability ELICITATION_CAPABILITY = new ClientCapability(ClientCapability.ELICITATION, Map.of());

    @Test
    public void testStreamableStateless() {
        try (var client = McpAssured.newStreamableClient()
                .setStateless()
                .setClientCapabilities(TASKS_CAPABILITY, ELICITATION_CAPABILITY)
                .build()
                .connect()) {
            assertInputRequired(client, true);
        }
    }

    @Test
    public void testSse() {
        try (var client = McpAssured.newSseClient()
                .setClientCapabilities(TASKS_CAPABILITY, ELICITATION_CAPABILITY)
                .build()
                .connect()) {
            assertInputRequired(client, false);
        }
    }

    <A extends McpAssert<A>> void assertInputRequired(McpTestClient<A, ?> client, boolean stateless) {
        String taskId = callToolAsTask(client, "greet");

        // The task waits for input
        JsonObject inputRequired = awaitStatus(client, taskId, stateless, TaskStatus.INPUT_REQUIRED);
        JsonObject inputRequests = inputRequired.getJsonObject("inputRequests");
        assertNotNull(inputRequests);
        assertEquals(2, inputRequests.size());
        JsonObject name = inputRequests.getJsonObject("name");
        assertEquals("elicitation/create", name.getString("method"));
        JsonObject params = name.getJsonObject("params");
        assertEquals("form", params.getString("mode"));
        assertEquals("Please enter your name.", params.getString("message"));
        assertEquals("string",
                params.getJsonObject("requestedSchema").getJsonObject("properties").getJsonObject("name")
                        .getString("type"));
        assertEquals("roots/list", inputRequests.getJsonObject("roots").getString("method"));

        // The same snapshot is returned until the requests are fulfilled
        assertEquals(inputRequests, getTask(client, taskId, stateless).getJsonObject("inputRequests"));

        // tasks/update requires inputResponses
        client.when()
                .message(newRequest(client, "tasks/update", new JsonObject().put("taskId", taskId), stateless))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, error.code());
                    assertTrue(error.message().contains("inputResponses"));
                })
                .send()
                .thenAssertResults();

        // A partial update - an unknown key is ignored and the task remains input_required
        client.when()
                .message(newRequest(client, "tasks/update", new JsonObject()
                        .put("taskId", taskId)
                        .put("inputResponses", new JsonObject()
                                .put("unknown", new JsonObject().put("action", "accept"))
                                .put("name", new JsonObject()
                                        .put("action", "accept")
                                        .put("content", new JsonObject().put("name", "Luca")))),
                        stateless))
                .withAssert(response -> {
                    JsonObject result = response.getJsonObject("result");
                    assertNotNull(result);
                    assertEquals("complete", result.getString("resultType"));
                })
                .send()
                .thenAssertResults();
        JsonObject partial = getTask(client, taskId, stateless);
        assertEquals(TaskStatus.INPUT_REQUIRED.jsonValue(), partial.getString("status"));
        assertEquals(1, partial.getJsonObject("inputRequests").size());
        assertNotNull(partial.getJsonObject("inputRequests").getJsonObject("roots"));

        // The remaining response completes the input request
        client.when()
                .message(newRequest(client, "tasks/update", new JsonObject()
                        .put("taskId", taskId)
                        .put("inputResponses", new JsonObject()
                                .put("roots", new JsonObject().put("roots", new io.vertx.core.json.JsonArray()
                                        .add(new JsonObject().put("uri", "file:///home").put("name", "home"))))),
                        stateless))
                .withAssert(response -> assertNotNull(response.getJsonObject("result")))
                .send()
                .thenAssertResults();

        JsonObject completed = awaitStatus(client, taskId, stateless, TaskStatus.COMPLETED);
        assertNull(completed.getJsonObject("inputRequests"));
        assertEquals("Hello, Luca! [file:///home]",
                completed.getJsonObject("result").getJsonArray("content").getJsonObject(0).getString("text"));
    }

    public static class MyTools {

        @Tool(description = "Greets the user")
        String greet(Tasks tasks, Elicitation elicitation) {
            throw tasks.newTask().setHandler(task -> {
                InputResponses responses = task.inputRequestBuilder()
                        .addElicitationRequest("name", elicitation.requestBuilder()
                                .setMessage("Please enter your name.")
                                .addSchemaProperty("name", new StringSchema(true))
                                .build())
                        .addRootsRequest("roots")
                        .build()
                        .sendAndAwait();
                ElicitationResponse name = responses.getElicitationResponse("name");
                String root = responses.getRootsResponse("roots").get(0).uri();
                return ToolResponse.success("Hello, " + name.content().getString("name") + "! [" + root + "]");
            }, false).create();
        }

    }

}
