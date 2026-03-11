package io.quarkiverse.mcp.server.test.prompts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class PromptsPaginationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.mcp.server.prompts.page-size", "3");

    @Inject
    PromptManager manager;

    @BeforeEach
    void removeAllPrompts() {
        List<String> names = new ArrayList<>();
        for (PromptManager.PromptInfo info : manager) {
            if (!info.isMethod()) {
                names.add(info.name());
            }
        }
        names.forEach(manager::removePrompt);
    }

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

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
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

    @Test
    public void testRemoveFromPreviousPage() {
        String[] names = { "p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8" };
        for (String name : names) {
            addPrompt(name);
        }

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        AtomicReference<String> cursor = new AtomicReference<>();

        client.when()
                .promptsList(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals("p1", page.prompts().get(0).name());
                    assertEquals("p2", page.prompts().get(1).name());
                    assertEquals("p3", page.prompts().get(2).name());
                }).thenAssertResults();

        // remove from previous page and add a new prompt
        manager.removePrompt("p2");
        addPrompt("p9");

        client.when()
                .promptsList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals("p4", page.prompts().get(0).name());
                    assertEquals("p5", page.prompts().get(1).name());
                    assertEquals("p6", page.prompts().get(2).name());
                })
                .send()
                .thenAssertResults();

        client.when()
                .promptsList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    assertNull(page.nextCursor());
                    assertEquals(2, page.size());
                    assertEquals("p7", page.prompts().get(0).name());
                    assertEquals("p8", page.prompts().get(1).name());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testRemoveFromUpcomingPage() {
        String[] names = { "q1", "q2", "q3", "q4", "q5", "q6", "q7", "q8" };
        for (String name : names) {
            addPrompt(name);
        }

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        AtomicReference<String> cursor = new AtomicReference<>();

        client.when()
                .promptsList(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals("q1", page.prompts().get(0).name());
                    assertEquals("q2", page.prompts().get(1).name());
                    assertEquals("q3", page.prompts().get(2).name());
                }).thenAssertResults();

        // remove q5 from upcoming page
        manager.removePrompt("q5");

        client.when()
                .promptsList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals("q4", page.prompts().get(0).name());
                    assertEquals("q6", page.prompts().get(1).name());
                    assertEquals("q7", page.prompts().get(2).name());
                })
                .send()
                .thenAssertResults();

        client.when()
                .promptsList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    assertNull(page.nextCursor());
                    assertEquals(1, page.size());
                    assertEquals("q8", page.prompts().get(0).name());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testRemoveAllRemaining() {
        String[] names = { "r1", "r2", "r3", "r4", "r5" };
        for (String name : names) {
            addPrompt(name);
        }

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        AtomicReference<String> cursor = new AtomicReference<>();

        client.when()
                .promptsList(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals("r1", page.prompts().get(0).name());
                    assertEquals("r2", page.prompts().get(1).name());
                    assertEquals("r3", page.prompts().get(2).name());
                }).thenAssertResults();

        // remove all remaining prompts
        manager.removePrompt("r4");
        manager.removePrompt("r5");

        client.when()
                .promptsList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    assertNull(page.nextCursor());
                    assertEquals(0, page.size());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testInvalidCursor() {
        addPrompt("dummy");

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .promptsList()
                .withCursor("not-a-valid-cursor")
                .withErrorAssert(error -> {
                    assertEquals(-32602, error.code());
                })
                .send()
                .thenAssertResults();
    }

    private void addPrompt(String name) {
        manager.newPrompt(name)
                .setDescription(name)
                .setHandler(
                        args -> PromptResponse
                                .withMessages(List.of(PromptMessage.withUserRole(new TextContent("Result: " + name)))))
                .register();
    }

}
