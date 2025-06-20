package io.quarkiverse.mcp.server.test.prompts.defaultvalues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.PromptInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.runtime.Startup;
import io.quarkus.test.QuarkusUnitTest;

public class PromptDefaultValuesTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyPrompts.class));

    @Test
    public void testPrompts() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();

        client.when()
                .promptsList(page -> {
                    assertEquals(2, page.size());

                    PromptInfo foo = page.findByName("foo");
                    assertEquals(2, foo.arguments().size());
                    assertEquals("name", foo.arguments().get(0).name());
                    assertTrue(foo.arguments().get(0).required());

                    PromptInfo bar = page.findByName("bar");
                    assertEquals(1, bar.arguments().size());
                    assertEquals("name", bar.arguments().get(0).name());
                    assertFalse(bar.arguments().get(0).required());
                })
                .promptsGet("foo", Map.of("name", "Lu"), promptResponse -> {
                    assertEquals("Lu_bazinga", promptResponse.messages().get(0).content().asText().text());
                })
                .promptsGet("bar", Map.of("name", "Lu"), promptResponse -> {
                    assertEquals("Lu", promptResponse.messages().get(0).content().asText().text());
                })
                .thenAssertResults();
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
