package io.quarkiverse.mcp.server.test.complete;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptCompletionManager;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class PromptProgrammaticCompleteTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyPrompts.class));

    static final List<String> NAMES = List.of("Martin", "Lu", "Jachym", "Vojtik", "Onda");

    @Inject
    PromptCompletionManager manager;

    @Test
    public void testCompletion() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();

        manager.newCompletion("foo")
                .setArgumentName("name")
                .setHandler(args -> new CompletionResponse(
                        NAMES.stream().filter(n -> n.startsWith(args.argumentValue())).toList(), 10, false))
                .register();

        manager.newCompletion("foo")
                .setArgumentName("suffix")
                .setHandler(args -> new CompletionResponse(List.of("_foo"), 1, false))
                .register();

        assertThrows(IllegalStateException.class, () -> manager.newCompletion("foo")
                .setArgumentName("nonexistent")
                .setHandler(args -> new CompletionResponse(
                        List.of(), 0, false))
                .register());

        client.when()
                .promptComplete("foo", "name", "Vo", completionResponse -> {
                    assertEquals(1, completionResponse.values().size());
                    assertEquals("Vojtik", completionResponse.values().get(0));
                })
                .promptComplete("foo")
                .withArgument("suffix", "Vo")
                .withAssert(completionResponse -> {
                    assertEquals(1, completionResponse.values().size());
                    assertEquals("_foo", completionResponse.values().get(0));
                })
                .send()
                .thenAssertResults();

        assertThrows(IllegalArgumentException.class,
                () -> manager.newCompletion("foo").setArgumentName("suffix").setHandler(args -> null).register());
    }

    public static class MyPrompts {

        @Prompt(description = "Not much we can say here.")
        PromptMessage foo(@PromptArg(description = "The name") String name, String suffix) {
            return new PromptMessage(Role.USER, new TextContent(name.toLowerCase() + suffix));
        }

    }
}
