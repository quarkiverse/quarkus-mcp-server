package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ToolsPaginationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.mcp.server.tools.page-size", "3");

    @Inject
    ToolManager manager;

    @BeforeEach
    void removeAllTools() {
        List<String> names = new ArrayList<>();
        for (ToolInfo info : manager) {
            if (!info.isMethod()) {
                names.add(info.name());
            }
        }
        names.forEach(manager::removeTool);
    }

    @Test
    public void testTools() {
        String[] names = { "foo", "bar", "baz", "alpha", "bravo", "charlie", "delta", "echo" };
        for (int i = 0; i < names.length; i++) {
            addTool(names[i]);
        }

        Instant lastCreatedAt = Instant.EPOCH;
        for (ToolInfo info : manager) {
            assertTrue(info.createdAt().isAfter(lastCreatedAt));
            lastCreatedAt = info.createdAt();
        }

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        AtomicReference<String> cursor = new AtomicReference<>();

        client.when()
                .toolsList(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals(names[0], page.tools().get(0).name());
                    assertEquals(names[1], page.tools().get(1).name());
                    assertEquals(names[2], page.tools().get(2).name());
                }).thenAssertResults();

        client.when()
                .toolsList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals(names[3], page.tools().get(0).name());
                    assertEquals(names[4], page.tools().get(1).name());
                    assertEquals(names[5], page.tools().get(2).name());
                })
                .send()
                .thenAssertResults();
        client.when()
                .toolsList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(2, page.size());
                    assertEquals(names[6], page.tools().get(0).name());
                    assertEquals(names[7], page.tools().get(1).name());
                })
                .send()
                .thenAssertResults();

        // start again
        client.when()
                .toolsList(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals(names[0], page.tools().get(0).name());
                    assertEquals(names[1], page.tools().get(1).name());
                    assertEquals(names[2], page.tools().get(2).name());
                })
                .thenAssertResults();

        // remove tool from the first page
        manager.removeTool(names[1]);
        // add tool "0" - this one should not be visible at all
        addTool("0");

        client.when()
                .toolsList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals(names[3], page.tools().get(0).name());
                    assertEquals(names[4], page.tools().get(1).name());
                    assertEquals(names[5], page.tools().get(2).name());
                })
                .send()
                .thenAssertResults();

        client.when()
                .toolsList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    assertEquals(2, page.size());
                    assertEquals(names[6], page.tools().get(0).name());
                    assertEquals(names[7], page.tools().get(1).name());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testRemoveFromUpcomingPage() {
        String[] names = { "t1", "t2", "t3", "t4", "t5", "t6", "t7", "t8" };
        for (String name : names) {
            addTool(name);
        }

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        AtomicReference<String> cursor = new AtomicReference<>();

        client.when()
                .toolsList(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals("t1", page.tools().get(0).name());
                    assertEquals("t2", page.tools().get(1).name());
                    assertEquals("t3", page.tools().get(2).name());
                }).thenAssertResults();

        // remove t5 from upcoming page
        manager.removeTool("t5");

        client.when()
                .toolsList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals("t4", page.tools().get(0).name());
                    assertEquals("t6", page.tools().get(1).name());
                    assertEquals("t7", page.tools().get(2).name());
                })
                .send()
                .thenAssertResults();

        client.when()
                .toolsList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    assertNull(page.nextCursor());
                    assertEquals(1, page.size());
                    assertEquals("t8", page.tools().get(0).name());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testRemoveAllRemaining() {
        String[] names = { "r1", "r2", "r3", "r4", "r5" };
        for (String name : names) {
            addTool(name);
        }

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        AtomicReference<String> cursor = new AtomicReference<>();

        client.when()
                .toolsList(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals("r1", page.tools().get(0).name());
                    assertEquals("r2", page.tools().get(1).name());
                    assertEquals("r3", page.tools().get(2).name());
                }).thenAssertResults();

        // remove all remaining tools
        manager.removeTool("r4");
        manager.removeTool("r5");

        client.when()
                .toolsList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    assertNull(page.nextCursor());
                    assertEquals(0, page.size());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testAddedToolsVisibleOnFreshListing() {
        String[] names = { "a1", "a2", "a3", "a4" };
        for (String name : names) {
            addTool(name);
        }

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        AtomicReference<String> cursor = new AtomicReference<>();

        client.when()
                .toolsList(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals("a1", page.tools().get(0).name());
                    assertEquals("a2", page.tools().get(1).name());
                    assertEquals("a3", page.tools().get(2).name());
                }).thenAssertResults();

        // add a new tool mid-pagination
        addTool("a5");

        // continue with cursor - a5 should NOT be visible
        client.when()
                .toolsList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    assertNull(page.nextCursor());
                    assertEquals(1, page.size());
                    assertEquals("a4", page.tools().get(0).name());
                })
                .send()
                .thenAssertResults();

        // fresh listing - a5 should now be visible
        client.when()
                .toolsList(page -> {
                    cursor.set(page.nextCursor());
                    assertNotNull(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals("a1", page.tools().get(0).name());
                    assertEquals("a2", page.tools().get(1).name());
                    assertEquals("a3", page.tools().get(2).name());
                }).thenAssertResults();

        client.when()
                .toolsList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    assertNull(page.nextCursor());
                    assertEquals(2, page.size());
                    assertEquals("a4", page.tools().get(0).name());
                    assertEquals("a5", page.tools().get(1).name());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testInvalidCursor() {
        addTool("dummy");

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsList()
                .withCursor("not-a-valid-cursor")
                .withErrorAssert(error -> {
                    assertEquals(-32602, error.code());
                })
                .send()
                .thenAssertResults();
    }

    private void addTool(String name) {
        manager.newTool(name)
                .setDescription(name)
                .setHandler(
                        args -> ToolResponse.success("Result " + name))
                .register();
    }

}
