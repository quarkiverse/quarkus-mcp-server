package io.quarkiverse.mcp.server.test.prompts.defaultvalues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.function.Consumer;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.runtime.Startup;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class PromptDefaultValuesTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyPrompts.class));

    @Test
    public void testPrompts() {
        initClient();

        JsonObject promptListMessage = newMessage("prompts/list");
        send(promptListMessage);

        JsonObject promptListResponse = waitForLastResponse();

        JsonObject promptListResult = assertResponseMessage(promptListMessage, promptListResponse);
        assertNotNull(promptListResult);
        JsonArray prompts = promptListResult.getJsonArray("prompts");
        assertEquals(2, prompts.size());

        assertPrompt(prompts.getJsonObject(0), "foo", null, args -> {
            assertEquals(2, args.size());
            JsonObject arg1 = args.getJsonObject(0);
            assertEquals("name", arg1.getString("name"));
            assertEquals(true, arg1.getBoolean("required"));
        });

        assertPrompt(prompts.getJsonObject(1), "bar", null, args -> {
            assertEquals(1, args.size());
            JsonObject arg1 = args.getJsonObject(0);
            assertEquals("name", arg1.getString("name"));
            assertEquals(false, arg1.getBoolean("required"));
        });

        assertPromptMessage("Lu_bazinga", "foo", new JsonObject()
                .put("name", "Lu"));
        assertPromptMessage("Lu", "bar", new JsonObject()
                .put("name", "Lu"));
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

    public static class MyPrompts {

        @Inject
        PromptManager manager;

        @Startup
        void start() {
            manager.newPrompt("bar")
                    .setDescription("Just bar...")
                    .addArgument("name", "The name", false, "Andy")
                    .setHandler(args -> PromptResponse.withMessages(PromptMessage.withUserRole(args.args().get("name"))))
                    .register();
        }

        @Prompt
        PromptMessage foo(@PromptArg(description = "The name") String name,
                @PromptArg(defaultValue = "_bazinga") String suffix) {
            return PromptMessage.withUserRole(new TextContent(name + suffix));
        }
    }

}
