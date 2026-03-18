package io.quarkiverse.mcp.server.schema.validator.test.prompts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.schema.validator.test.McpServerTest;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class PromptsSchemaValidationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyPrompts.class));

    @Test
    public void testPrompts() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .promptsList(page -> {
                    assertEquals(1, page.size());
                    assertEquals("greeting", page.prompts().get(0).name());
                })
                .promptsGet("greeting", Map.of("name", "Lucy"), r -> {
                    assertEquals("Hello Lucy!", r.messages().get(0).content().asText().text());
                })
                // Send a prompts/list request with invalid cursor type (must be string)
                .message(client.newRequest("prompts/list").put("params", new JsonObject().put("cursor", 123)))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertTrue(error.message().startsWith("Schema validation failed"));
                })
                .send()
                // Send a prompts/get request without the required "params" field
                .message(client.newRequest("prompts/get"))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertTrue(error.message().startsWith("Schema validation failed"));
                })
                .send()
                .thenAssertResults();
    }

    public static class MyPrompts {

        @Prompt
        PromptMessage greeting(String name) {
            return PromptMessage.withUserRole("Hello " + name + "!");
        }

    }

}
