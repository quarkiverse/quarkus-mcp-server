package io.quarkiverse.mcp.server.test.resources.templates;

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

import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ResourceTemplatesPaginationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.mcp.server.resource-templates.page-size", "3");

    @Inject
    ResourceTemplateManager manager;

    @BeforeEach
    void removeAllTemplates() {
        List<String> names = new ArrayList<>();
        for (ResourceTemplateManager.ResourceTemplateInfo info : manager) {
            if (!info.isMethod()) {
                names.add(info.name());
            }
        }
        names.forEach(manager::removeResourceTemplate);
    }

    @Test
    public void testResourceTemplates() {
        int loop = 8;
        for (int i = 1; i <= loop; i++) {
            String name = i + "";
            manager.newResourceTemplate(name)
                    .setUriTemplate("file:///" + name)
                    .setDescription(name)
                    .setHandler(
                            args -> new ResourceResponse(
                                    List.of(TextResourceContents.create(args.requestUri().value(), "Result: " + name))))
                    .register();
        }

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        AtomicReference<String> cursor = new AtomicReference<>();

        client.when()
                .resourcesTemplatesList(p -> {
                    cursor.set(p.nextCursor());
                    assertEquals(3, p.size());
                    assertEquals("1", p.findByUriTemplate("file:///1").name());
                    assertEquals("2", p.findByUriTemplate("file:///2").name());
                    assertEquals("3", p.findByUriTemplate("file:///3").name());
                })
                .thenAssertResults();

        client.when()
                .resourcesTemplatesList()
                .withCursor(cursor.get())
                .withAssert(p -> {
                    cursor.set(p.nextCursor());
                    assertEquals(3, p.size());
                    assertEquals("4", p.findByUriTemplate("file:///4").name());
                    assertEquals("5", p.findByUriTemplate("file:///5").name());
                    assertEquals("6", p.findByUriTemplate("file:///6").name());
                })
                .send()
                .thenAssertResults();

        client.when()
                .resourcesTemplatesList()
                .withCursor(cursor.get())
                .withAssert(p -> {
                    assertEquals(2, p.size());
                    assertEquals("7", p.findByUriTemplate("file:///7").name());
                    assertEquals("8", p.findByUriTemplate("file:///8").name());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testRemoveFromPreviousPage() {
        String[] names = { "t1", "t2", "t3", "t4", "t5", "t6", "t7", "t8" };
        for (String name : names) {
            addTemplate(name);
        }

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        AtomicReference<String> cursor = new AtomicReference<>();

        client.when()
                .resourcesTemplatesList(p -> {
                    cursor.set(p.nextCursor());
                    assertNotNull(p.nextCursor());
                    assertEquals(3, p.size());
                    assertEquals("t1", p.findByUriTemplate("file:///t1").name());
                    assertEquals("t2", p.findByUriTemplate("file:///t2").name());
                    assertEquals("t3", p.findByUriTemplate("file:///t3").name());
                }).thenAssertResults();

        // remove from previous page and add a new template
        manager.removeResourceTemplate("t2");
        addTemplate("t9");

        client.when()
                .resourcesTemplatesList()
                .withCursor(cursor.get())
                .withAssert(p -> {
                    cursor.set(p.nextCursor());
                    assertNotNull(p.nextCursor());
                    assertEquals(3, p.size());
                    assertEquals("t4", p.findByUriTemplate("file:///t4").name());
                    assertEquals("t5", p.findByUriTemplate("file:///t5").name());
                    assertEquals("t6", p.findByUriTemplate("file:///t6").name());
                })
                .send()
                .thenAssertResults();

        client.when()
                .resourcesTemplatesList()
                .withCursor(cursor.get())
                .withAssert(p -> {
                    assertNull(p.nextCursor());
                    assertEquals(2, p.size());
                    assertEquals("t7", p.findByUriTemplate("file:///t7").name());
                    assertEquals("t8", p.findByUriTemplate("file:///t8").name());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testRemoveFromUpcomingPage() {
        String[] names = { "u1", "u2", "u3", "u4", "u5", "u6", "u7", "u8" };
        for (String name : names) {
            addTemplate(name);
        }

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        AtomicReference<String> cursor = new AtomicReference<>();

        client.when()
                .resourcesTemplatesList(p -> {
                    cursor.set(p.nextCursor());
                    assertEquals(3, p.size());
                    assertEquals("u1", p.findByUriTemplate("file:///u1").name());
                    assertEquals("u2", p.findByUriTemplate("file:///u2").name());
                    assertEquals("u3", p.findByUriTemplate("file:///u3").name());
                }).thenAssertResults();

        // remove u5 from upcoming page
        manager.removeResourceTemplate("u5");

        client.when()
                .resourcesTemplatesList()
                .withCursor(cursor.get())
                .withAssert(p -> {
                    cursor.set(p.nextCursor());
                    assertEquals(3, p.size());
                    assertEquals("u4", p.findByUriTemplate("file:///u4").name());
                    assertEquals("u6", p.findByUriTemplate("file:///u6").name());
                    assertEquals("u7", p.findByUriTemplate("file:///u7").name());
                })
                .send()
                .thenAssertResults();

        client.when()
                .resourcesTemplatesList()
                .withCursor(cursor.get())
                .withAssert(p -> {
                    assertNull(p.nextCursor());
                    assertEquals(1, p.size());
                    assertEquals("u8", p.findByUriTemplate("file:///u8").name());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testRemoveAllRemaining() {
        String[] names = { "v1", "v2", "v3", "v4", "v5" };
        for (String name : names) {
            addTemplate(name);
        }

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        AtomicReference<String> cursor = new AtomicReference<>();

        client.when()
                .resourcesTemplatesList(p -> {
                    cursor.set(p.nextCursor());
                    assertEquals(3, p.size());
                    assertEquals("v1", p.findByUriTemplate("file:///v1").name());
                    assertEquals("v2", p.findByUriTemplate("file:///v2").name());
                    assertEquals("v3", p.findByUriTemplate("file:///v3").name());
                }).thenAssertResults();

        // remove all remaining templates
        manager.removeResourceTemplate("v4");
        manager.removeResourceTemplate("v5");

        client.when()
                .resourcesTemplatesList()
                .withCursor(cursor.get())
                .withAssert(p -> {
                    assertNull(p.nextCursor());
                    assertEquals(0, p.size());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testInvalidCursor() {
        addTemplate("dummy");

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .resourcesTemplatesList()
                .withCursor("not-a-valid-cursor")
                .withErrorAssert(error -> {
                    assertEquals(-32602, error.code());
                })
                .send()
                .thenAssertResults();
    }

    private void addTemplate(String name) {
        manager.newResourceTemplate(name)
                .setUriTemplate("file:///" + name)
                .setDescription(name)
                .setHandler(
                        args -> new ResourceResponse(
                                List.of(TextResourceContents.create(args.requestUri().value(), "Result: " + name))))
                .register();
    }

}
