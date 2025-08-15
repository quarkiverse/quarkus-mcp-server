package io.quarkiverse.mcp.server.test.complete;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.CompleteArg;
import io.quarkiverse.mcp.server.CompleteContext;
import io.quarkiverse.mcp.server.CompletePrompt;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptCompletionManager;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class PromptCompleteContextTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyPrompts.class));

    @Inject
    PromptCompletionManager manager;

    @Test
    public void testCompletion() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();

        client.when()
                .promptComplete("foo", "name", "Vo", completionResponse -> {
                    assertEquals(1, completionResponse.values().size());
                    assertEquals("def", completionResponse.values().get(0));
                })
                .promptComplete("foo")
                .withArgument("name", "Vo")
                .withContext(Map.of("Vo", "nondef"))
                .withAssert(completionResponse -> {
                    assertEquals(1, completionResponse.values().size());
                    assertEquals("nondef", completionResponse.values().get(0));
                })
                .send()
                .thenAssertResults();

        assertThrows(IllegalArgumentException.class,
                () -> manager.newCompletion("foo").setArgumentName("name").setHandler(args -> null).register());
    }

    static class MyPrompts {

        @Prompt(description = "Not much we can say here.")
        PromptMessage foo(@PromptArg(description = "The name") String name) {
            return PromptMessage.withUserRole(new TextContent(name.toLowerCase()));
        }

        @CompletePrompt("foo")
        String completeName(CompleteContext context, @CompleteArg(name = "name") String val) {
            return context.arguments().getOrDefault(val, "def");
        }

    }

}
