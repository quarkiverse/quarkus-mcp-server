package io.quarkiverse.mcp.server.test.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ResourceInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ResourcesPaginationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.mcp.server.resources.page-size", "3");

    @Inject
    ResourceManager manager;

    @BeforeEach
    void removeAllResources() {
        List<String> uris = new ArrayList<>();
        for (ResourceManager.ResourceInfo info : manager) {
            if (!info.isMethod()) {
                uris.add(info.uri());
            }
        }
        uris.forEach(manager::removeResource);
    }

    @Test
    public void testResources() {
        int loop = 8;
        for (int i = 1; i <= loop; i++) {
            String name = i + "";
            manager.newResource(name)
                    .setUri("file:///" + name)
                    .setDescription(name)
                    .setHandler(
                            args -> new ResourceResponse(
                                    List.of(TextResourceContents.create(args.requestUri().value(), "Result: " + name))))
                    .register();
        }

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        AtomicReference<String> cursor = new AtomicReference<>();

        client.when()
                .resourcesList(page -> {
                    assertEquals(3, page.size());
                    assertNotNull(page.nextCursor());
                    ResourceInfo r1 = page.resources().get(0);
                    assertEquals("1", r1.name());
                    assertEquals("1", r1.description());
                    assertEquals("file:///1", r1.uri());
                    ResourceInfo r2 = page.resources().get(1);
                    assertEquals("2", r2.name());
                    assertEquals("2", r2.description());
                    assertEquals("file:///2", r2.uri());
                    ResourceInfo r3 = page.resources().get(2);
                    assertEquals("3", r3.name());
                    assertEquals("3", r3.description());
                    assertEquals("file:///3", r3.uri());
                    cursor.set(page.nextCursor());
                })
                .thenAssertResults();

        client.when()
                .resourcesList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    assertEquals(3, page.size());
                    assertNotNull(page.nextCursor());
                    ResourceInfo r4 = page.resources().get(0);
                    assertEquals("4", r4.name());
                    assertEquals("4", r4.description());
                    assertEquals("file:///4", r4.uri());
                    ResourceInfo r5 = page.resources().get(1);
                    assertEquals("5", r5.name());
                    assertEquals("5", r5.description());
                    assertEquals("file:///5", r5.uri());
                    ResourceInfo r6 = page.resources().get(2);
                    assertEquals("6", r6.name());
                    assertEquals("6", r6.description());
                    assertEquals("file:///6", r6.uri());
                    cursor.set(page.nextCursor());
                }).send()
                .thenAssertResults();

        client.when()
                .resourcesList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    assertEquals(2, page.size());
                    assertNull(page.nextCursor());
                    ResourceInfo r7 = page.resources().get(0);
                    assertEquals("7", r7.name());
                    assertEquals("7", r7.description());
                    assertEquals("file:///7", r7.uri());
                    ResourceInfo r8 = page.resources().get(1);
                    assertEquals("8", r8.name());
                    assertEquals("8", r8.description());
                    assertEquals("file:///8", r8.uri());
                }).send()
                .thenAssertResults();
    }

    @Test
    public void testRemoveFromPreviousPage() {
        String[] names = { "r1", "r2", "r3", "r4", "r5", "r6", "r7", "r8" };
        for (String name : names) {
            addResource(name);
        }

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        AtomicReference<String> cursor = new AtomicReference<>();

        client.when()
                .resourcesList(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertNotNull(page.nextCursor());
                    assertEquals("r1", page.resources().get(0).name());
                    assertEquals("r2", page.resources().get(1).name());
                    assertEquals("r3", page.resources().get(2).name());
                }).thenAssertResults();

        // remove from previous page and add a new resource
        manager.removeResource("file:///r2");
        addResource("r9");

        client.when()
                .resourcesList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertNotNull(page.nextCursor());
                    assertEquals("r4", page.resources().get(0).name());
                    assertEquals("r5", page.resources().get(1).name());
                    assertEquals("r6", page.resources().get(2).name());
                })
                .send()
                .thenAssertResults();

        client.when()
                .resourcesList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    assertNull(page.nextCursor());
                    assertEquals(2, page.size());
                    assertEquals("r7", page.resources().get(0).name());
                    assertEquals("r8", page.resources().get(1).name());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testRemoveFromUpcomingPage() {
        String[] names = { "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8" };
        for (String name : names) {
            addResource(name);
        }

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        AtomicReference<String> cursor = new AtomicReference<>();

        client.when()
                .resourcesList(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals("s1", page.resources().get(0).name());
                    assertEquals("s2", page.resources().get(1).name());
                    assertEquals("s3", page.resources().get(2).name());
                }).thenAssertResults();

        // remove s5 from upcoming page
        manager.removeResource("file:///s5");

        client.when()
                .resourcesList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals("s4", page.resources().get(0).name());
                    assertEquals("s6", page.resources().get(1).name());
                    assertEquals("s7", page.resources().get(2).name());
                })
                .send()
                .thenAssertResults();

        client.when()
                .resourcesList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    assertNull(page.nextCursor());
                    assertEquals(1, page.size());
                    assertEquals("s8", page.resources().get(0).name());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testRemoveAllRemaining() {
        String[] names = { "t1", "t2", "t3", "t4", "t5" };
        for (String name : names) {
            addResource(name);
        }

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        AtomicReference<String> cursor = new AtomicReference<>();

        client.when()
                .resourcesList(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals("t1", page.resources().get(0).name());
                    assertEquals("t2", page.resources().get(1).name());
                    assertEquals("t3", page.resources().get(2).name());
                }).thenAssertResults();

        // remove all remaining resources
        manager.removeResource("file:///t4");
        manager.removeResource("file:///t5");

        client.when()
                .resourcesList()
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
        addResource("dummy");

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .resourcesList()
                .withCursor("not-a-valid-cursor")
                .withErrorAssert(error -> {
                    assertEquals(-32602, error.code());
                })
                .send()
                .thenAssertResults();
    }

    private void addResource(String name) {
        manager.newResource(name)
                .setUri("file:///" + name)
                .setDescription(name)
                .setHandler(
                        args -> new ResourceResponse(
                                List.of(TextResourceContents.create(args.requestUri().value(), "Result: " + name))))
                .register();
    }

}
