package io.quarkiverse.mcp.server.test.prompts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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
import io.vertx.core.json.JsonObject;

public class ProgrammaticPromptTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyPrompts.class));

    @Inject
    MyPrompts myPrompts;

    @Test
    public void testPrompts() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .promptsList(page -> {
                    assertEquals(0, page.size());
                })
                .promptsGet("alpha")
                .withErrorAssert(e -> assertEquals("Invalid prompt name: alpha", e.message()))
                .send()
                .thenAssertResults();

        myPrompts.register("alpha", "2");
        assertThrows(IllegalArgumentException.class, () -> myPrompts.register("alpha", "2"));
        assertThrows(NullPointerException.class, () -> myPrompts.register(null, "2"));

        List<JsonObject> notifications = client.waitForNotifications(1).notifications();
        assertEquals("notifications/prompts/list_changed", notifications.get(0).getString("method"));

        client.when()
                .promptsList(page -> {
                    assertEquals(1, page.size());
                })
                .promptsGet("alpha", Map.of("foo", "2"), r -> {
                    assertEquals("22", r.messages().get(0).content().asText().text());
                })
                .thenAssertResults();

        myPrompts.register("bravo", "3");

        notifications = client.waitForNotifications(2).notifications();
        assertEquals("notifications/prompts/list_changed", notifications.get(1).getString("method"));

        client.when()
                .promptsList(page -> {
                    assertEquals(2, page.size());
                })
                .promptsGet("bravo", Map.of("foo", "3"), r -> {
                    assertEquals("33", r.messages().get(0).content().asText().text());
                })
                .thenAssertResults();

        myPrompts.remove("alpha");

        client.when()
                .promptsList(page -> {
                    assertEquals(1, page.size());
                })
                .promptsGet("alpha")
                .withErrorAssert(e -> assertEquals("Invalid prompt name: alpha", e.message()))
                .send()
                .promptsGet("bravo", Map.of("foo", "2"), r -> {
                    assertEquals("32", r.messages().get(0).content().asText().text());
                })
                .thenAssertResults();
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
