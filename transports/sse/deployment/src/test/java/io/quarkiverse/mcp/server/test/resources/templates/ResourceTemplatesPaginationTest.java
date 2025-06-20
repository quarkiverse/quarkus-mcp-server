package io.quarkiverse.mcp.server.test.resources.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ResourceTemplatesPaginationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.mcp.server.resource-templates.page-size", "3");

    @Inject
    ResourceTemplateManager manager;

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

        McpSseTestClient client = McpAssured.newConnectedSseClient();
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

}
