package io.quarkiverse.mcp.server.test.prompts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URISyntaxException;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class PromptsPaginationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.mcp.server.prompts.page-size", "3");

    @Inject
    PromptManager manager;

    @Test
    public void testResources() throws URISyntaxException {
        int loop = 8;
        for (int i = 1; i <= loop; i++) {
            String name = i + "";
            manager.newPrompt(name)
                    .setDescription(name)
                    .setHandler(
                            args -> PromptResponse
                                    .withMessages(List.of(PromptMessage.withUserRole(new TextContent("Result: " + name)))))
                    .register();
        }

        initClient();

        JsonObject message = newMessage("prompts/list");
        send(message);

        JsonObject response = waitForLastResponse();
        JsonObject result = assertResponseMessage(message, response);
        assertNotNull(result);
        JsonArray prompts = result.getJsonArray("prompts");
        assertEquals(3, prompts.size());
        String cursor = result.getString("nextCursor");
        assertNotNull(cursor);

        assertPrompt(prompts.getJsonObject(0), "1");
        assertPrompt(prompts.getJsonObject(1), "2");
        assertPrompt(prompts.getJsonObject(2), "3");

        message = newMessage("prompts/list").put("params", new JsonObject().put("cursor", cursor));
        send(message);

        response = waitForLastResponse();
        result = assertResponseMessage(message, response);
        assertNotNull(result);
        prompts = result.getJsonArray("prompts");
        assertEquals(3, prompts.size());
        cursor = result.getString("nextCursor");
        assertNotNull(cursor);

        assertPrompt(prompts.getJsonObject(0), "4");
        assertPrompt(prompts.getJsonObject(1), "5");
        assertPrompt(prompts.getJsonObject(2), "6");

        message = newMessage("prompts/list").put("params", new JsonObject().put("cursor", cursor));
        send(message);

        response = waitForLastResponse();
        result = assertResponseMessage(message, response);
        assertNotNull(result);
        prompts = result.getJsonArray("prompts");
        assertEquals(2, prompts.size());
        assertNull(result.getString("nextCursor"));

        assertPrompt(prompts.getJsonObject(0), "7");
        assertPrompt(prompts.getJsonObject(1), "8");
    }

    private void assertPrompt(JsonObject prompt, String name) {
        assertEquals(name, prompt.getString("name"));
        assertEquals(name, prompt.getString("description"));
    }

}
