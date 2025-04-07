package io.quarkiverse.mcp.server.test.prompts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.FooService;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkiverse.mcp.server.test.Options;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class PromptsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(FooService.class, Options.class, Checks.class, MyPrompts.class));

    @Test
    public void testPrompts() {
        initClient();
        JsonObject promptListMessage = newMessage("prompts/list");
        send(promptListMessage);

        JsonObject promptListResponse = waitForLastResponse();

        JsonObject promptListResult = assertResponseMessage(promptListMessage, promptListResponse);
        assertNotNull(promptListResult);
        JsonArray prompts = promptListResult.getJsonArray("prompts");
        assertEquals(6, prompts.size());

        assertPrompt(prompts.getJsonObject(0), "BAR", null, args -> {
            assertEquals(1, args.size());
            JsonObject arg1 = args.getJsonObject(0);
            assertEquals("val", arg1.getString("name"));
            assertEquals(true, arg1.getBoolean("required"));
        });
        assertPrompt(prompts.getJsonObject(1), "foo", "Not much we can say here.", args -> {
            assertEquals(2, args.size());
        });

        assertPromptMessage("Hello Lu!", "foo", new JsonObject()
                .put("name", "Lu")
                .put("repeat", "1"));
        assertPromptMessage("JACHYM", "BAR", new JsonObject()
                .put("val", "Jachym"));
        assertPromptMessage("VOJTECH", "uni_bar", new JsonObject()
                .put("val", "Vojtech"));
        assertPromptMessage("ONDREJ", "uni_list_bar", new JsonObject()
                .put("val", "Ondrej"));
        assertPromptMessage("MARTIN", "response", new JsonObject()
                .put("val", "Martin"));
        assertPromptMessage("MARTIN", "uni_response", new JsonObject()
                .put("val", "Martin"));
    }

    private void assertPrompt(JsonObject prompt, String name, String description, Consumer<JsonArray> argumentsAsserter) {
        assertEquals(name, prompt.getString("name"));
        if (description != null) {
            assertEquals(description, prompt.getString("description"));
        }
        if (argumentsAsserter != null) {
            argumentsAsserter.accept(prompt.getJsonArray("arguments"));
        }
    }

    private void assertPromptMessage(String expectedText, String name, JsonObject arguments) {
        JsonObject promptGetMessage = newMessage("prompts/get")
                .put("params", new JsonObject()
                        .put("name", name)
                        .put("arguments", arguments));
        send(promptGetMessage);

        JsonObject promptGetResponse = waitForLastResponse();

        JsonObject promptGetResult = assertResponseMessage(promptGetMessage, promptGetResponse);
        assertNotNull(promptGetResult);
        JsonArray messages = promptGetResult.getJsonArray("messages");
        assertEquals(1, messages.size());
        JsonObject message = messages.getJsonObject(0);
        assertEquals("user", message.getString("role"));
        JsonObject content = message.getJsonObject("content");
        assertEquals("text", content.getString("type"));
        assertEquals(expectedText, content.getString("text"));
    }

}
