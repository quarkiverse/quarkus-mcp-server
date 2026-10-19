package io.quarkiverse.mcp.server.test.prompts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptManager.PromptArguments;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class NullProgrammaticPromptArgumentTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig();

    @Inject
    PromptManager promptManager;

    @Test
    public void testNullArgumentValue() {
        promptManager.newPrompt("optional")
                .setDescription("Prompt with an optional argument")
                .addArgument("source", "Source id", false)
                .setHandler(NullProgrammaticPromptArgumentTest::handlePrompt)
                .register();
        promptManager.newPrompt("with-default")
                .setDescription("Prompt with an optional argument that has a default value")
                .addArgument("source", "Source id", false, "default-source")
                .setHandler(NullProgrammaticPromptArgumentTest::handlePrompt)
                .register();
        // A JSON null is treated like a missing argument
        Map<String, String> args = new HashMap<>();
        args.put("source", null);

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .promptsGet("optional", args, r -> {
                    assertEquals("absent:null", r.firstMessage().content().asText().text());
                })
                .promptsGet("with-default", args, r -> {
                    assertEquals("present:default-source", r.firstMessage().content().asText().text());
                })
                .thenAssertResults();
    }

    private static PromptResponse handlePrompt(PromptArguments promptArguments) {
        Map<String, String> args = promptArguments.args();
        return PromptResponse.withMessages(List.of(PromptMessage.withUserRole(new TextContent(
                (args.containsKey("source") ? "present:" : "absent:") + args.get("source")))));
    }

}
