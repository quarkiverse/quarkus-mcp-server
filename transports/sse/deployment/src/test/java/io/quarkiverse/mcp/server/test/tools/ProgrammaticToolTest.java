package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URISyntaxException;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ProgrammaticToolTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Inject
    MyTools myTools;

    @Test
    public void testTools() throws URISyntaxException {
        initClient();
        assertTools(0);
        assertToolCallResponseError("alpha");

        myTools.register("alpha", "2");
        assertThrows(IllegalArgumentException.class, () -> myTools.register("alpha", "2"));
        assertThrows(NullPointerException.class, () -> myTools.register(null, "2"));

        List<JsonObject> notifications = client().waitForNotifications(1);
        assertEquals("notifications/tools/list_changed", notifications.get(0).getString("method"));

        assertTools(1);
        assertToolCallResponse("alpha", new JsonObject().put("foo", 2), "22");

        myTools.register("bravo", "3");

        assertTools(2);
        assertEquals("notifications/tools/list_changed", client().waitForNotifications(1).get(0).getString("method"));
        assertToolCallResponse("bravo", new JsonObject().put("foo", 3), "33");

        myTools.remove("alpha");
        assertTools(1);
        assertToolCallResponseError("alpha");
        assertToolCallResponse("bravo", new JsonObject().put("foo", 4), "34");
    }

    private void assertTools(int expectedSize) {
        JsonObject toolsListMessage = newMessage("tools/list");
        send(toolsListMessage);

        JsonObject toolsListResponse = waitForLastResponse();

        JsonObject toolsListResult = assertResponseMessage(toolsListMessage, toolsListResponse);
        assertNotNull(toolsListResult);
        JsonArray tools = toolsListResult.getJsonArray("tools");
        assertEquals(expectedSize, tools.size());
    }

    private void assertToolCallResponseError(String name) {
        JsonObject message = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", name));
        send(message);
        JsonObject response = waitForLastResponse();
        assertEquals(JsonRPC.INVALID_PARAMS, response.getJsonObject("error").getInteger("code"));
        assertEquals("Invalid tool name: " + name, response.getJsonObject("error").getString("message"));

    }

    private void assertToolCallResponse(String name, JsonObject arguments, String expectedText) {
        JsonObject message = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", name)
                        .put("arguments", arguments));
        send(message);
        JsonObject toolResponse = waitForLastResponse();
        JsonObject toolResult = assertResponseMessage(message, toolResponse);
        assertNotNull(toolResult);
        assertFalse(toolResult.getBoolean("isError"));
        JsonArray content = toolResult.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals(expectedText, textContent.getString("text"));
    }

    @Singleton
    public static class MyTools {

        @Inject
        ToolManager manager;

        void register(String name, String result) {
            manager.newTool(name)
                    .setDescription(name + " description!")
                    .addArgument("foo", "Foo arg", true, int.class)
                    .setHandler(
                            args -> ToolResponse.success(result + args.args().get("foo")))
                    .register();
        }

        ToolManager.ToolInfo remove(String name) {
            return manager.removeTool(name);
        }

    }

}
