package io.quarkiverse.mcp.server.test.prompts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class PromptsPaginationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.mcp.server.prompts.page-size", "3");

    @Inject
    PromptManager manager;

    @Test
    public void testPrompts() {
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

        McpSseTestClient client = McpAssured.newConnectedSseClient();
        AtomicReference<String> cursor = new AtomicReference<>();
        client.when()
                .promptsList(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals("1", page.prompts().get(0).name());
                    assertEquals("2", page.prompts().get(1).name());
                    assertEquals("3", page.prompts().get(2).name());
                })
                .thenAssertResults();

        client.when()
                .promptsList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals("4", page.prompts().get(0).name());
                    assertEquals("5", page.prompts().get(1).name());
                    assertEquals("6", page.prompts().get(2).name());
                })
                .send()
                .thenAssertResults();

        client.when()
                .promptsList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    assertEquals(2, page.size());
                    assertEquals("7", page.prompts().get(0).name());
                    assertEquals("8", page.prompts().get(1).name());
                })
                .send()
                .thenAssertResults();
    }

}
