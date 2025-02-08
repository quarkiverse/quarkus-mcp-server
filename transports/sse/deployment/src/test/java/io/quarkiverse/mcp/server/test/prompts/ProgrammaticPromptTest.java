package io.quarkiverse.mcp.server.test.prompts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URISyntaxException;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ProgrammaticPromptTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyPrompts.class));

    @Inject
    MyPrompts myPrompts;

    @Test
    public void testPrompts() throws URISyntaxException {
        initClient();
        assertPrompts(0);
        assertPromptGetResponseError("alpha");

        myPrompts.register("alpha", "2");
        assertThrows(IllegalArgumentException.class, () -> myPrompts.register("alpha", "2"));
        assertThrows(NullPointerException.class, () -> myPrompts.register(null, "2"));

        List<JsonObject> notifications = client().waitForNotifications(1);
        assertEquals("notifications/prompts/list_changed", notifications.get(0).getString("method"));

        assertPrompts(1);
        assertPromptGetResponse("alpha", new JsonObject().put("foo", 2), "22");

        myPrompts.register("bravo", "3");

        assertPrompts(2);
        assertEquals("notifications/prompts/list_changed", client().waitForNotifications(1).get(0).getString("method"));
        assertPromptGetResponse("bravo", new JsonObject().put("foo", 3), "33");

        myPrompts.remove("alpha");
        assertPrompts(1);
        assertPromptGetResponseError("alpha");
        assertPromptGetResponse("bravo", new JsonObject().put("foo", 2), "32");
    }

    private void assertPrompts(int expectedSize) {
        JsonObject promptsListMessage = newMessage("prompts/list");
        send(promptsListMessage);

        JsonObject promptsListResponse = waitForLastResponse();

        JsonObject promptsListResult = assertResponseMessage(promptsListMessage, promptsListResponse);
        assertNotNull(promptsListResult);
        JsonArray prompts = promptsListResult.getJsonArray("prompts");
        assertEquals(expectedSize, prompts.size());
    }

    private void assertPromptGetResponseError(String name) {
        JsonObject message = newMessage("prompts/get")
                .put("params", new JsonObject()
                        .put("name", name));
        send(message);
        JsonObject response = waitForLastResponse();
        assertEquals(JsonRPC.INVALID_PARAMS, response.getJsonObject("error").getInteger("code"));
        assertEquals("Invalid prompt name: " + name, response.getJsonObject("error").getString("message"));

    }

    private void assertPromptGetResponse(String name, JsonObject arguments, String expectedText) {
        JsonObject message = newMessage("prompts/get")
                .put("params", new JsonObject()
                        .put("name", name)
                        .put("arguments", arguments));
        send(message);
        JsonObject resourceResponse = waitForLastResponse();
        JsonObject resourceResult = assertResponseMessage(message, resourceResponse);
        assertNotNull(resourceResult);
        JsonArray messages = resourceResult.getJsonArray("messages");
        assertEquals(1, messages.size());
        JsonObject textContent = messages.getJsonObject(0).getJsonObject("content");
        assertEquals(expectedText, textContent.getString("text"));
    }

    @Singleton
    public static class MyPrompts {

        @Inject
        PromptManager manager;

        void register(String name, String result) {
            manager.newPrompt(name)
                    .setDescription(name + " description!")
                    .addArgument("foo", "Foo", true)
                    .setHandler(
                            args -> PromptResponse.withMessages(
                                    List.of(PromptMessage.withUserRole(new TextContent(result + args.args().get("foo"))))))
                    .register();
        }

        PromptManager.PromptInfo remove(String name) {
            return manager.removePrompt(name);
        }

    }

}
