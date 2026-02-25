package io.quarkiverse.mcp.server.test.resources.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ResourceTemplateInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ResourceTemplatesTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTemplates.class, Checks.class, AlwaysError.class, AlwaysErrorInterceptor.class));

    @Inject
    ResourceTemplateManager manager;

    @Test
    public void testResourceTemplates() {
        ResourceTemplateManager.ResourceTemplateInfo foxtrotTemplate = manager.getResourceTemplate("foxtrot_custom_name");
        assertNotNull(foxtrotTemplate);
        assertEquals("Foxtrot Resource", foxtrotTemplate.title());
        assertEquals("file:///foxtrot/{path}", foxtrotTemplate.uriTemplate());
        assertEquals("application/json", foxtrotTemplate.mimeType());
        assertEquals("customValue", foxtrotTemplate.metadata().get(MetaKey.of("customField")));
        ResourceTemplateManager.ResourceTemplateInfo golfTemplate = manager.getResourceTemplate("golf");
        assertNotNull(golfTemplate);
        assertEquals(Role.ASSISTANT, golfTemplate.annotations().orElseThrow().audience());
        assertEquals(0.7, golfTemplate.annotations().orElseThrow().priority());
        assertEquals("2024-01-15T10:30:00Z", golfTemplate.annotations().orElseThrow().lastModified());

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .resourcesTemplatesList(p -> {
                    assertEquals(7, p.size());

                    ResourceTemplateInfo alpha = p.findByUriTemplate("file:///{path}");
                    assertEquals("Alpha...", alpha.title());
                    assertNotNull(alpha.annotations());
                    assertEquals(Role.USER, alpha.annotations().audience());
                    assertEquals(0.5, alpha.annotations().priority());

                    ResourceTemplateInfo foxtrot = p.findByUriTemplate("file:///foxtrot/{path}");
                    assertEquals("foxtrot_custom_name", foxtrot.name());
                    assertEquals("Foxtrot Resource", foxtrot.title());
                    assertEquals("This is a detailed description for the foxtrot resource template",
                            foxtrot.description());
                    assertEquals("application/json", foxtrot.mimeType());
                    assertNotNull(foxtrot.meta());
                    JsonObject foxMeta = foxtrot.meta();
                    assertEquals("customValue", foxMeta.getString("customField"));
                    assertEquals(2, foxMeta.getInteger("version"));
                    assertTrue(foxMeta.getBoolean("enabled"));

                    ResourceTemplateInfo golf = p.findByUriTemplate("file:///golf/{id}");
                    assertEquals("golf", golf.name());
                    assertEquals("Golf Resource", golf.title());
                    assertEquals("Resource with lastModified annotation", golf.description());
                    assertEquals("text/plain", golf.mimeType());
                    assertNotNull(golf.annotations());
                    assertEquals(Role.ASSISTANT, golf.annotations().audience());
                    assertEquals("2024-01-15T10:30:00Z", golf.annotations().lastModified());
                    assertEquals(0.7, golf.annotations().priority());
                })
                .resourcesRead("file:///bar", r -> assertEquals("foo:bar", r.contents().get(0).asText().text()))
                .resourcesRead("file:///bravo/bar/baz", r -> assertEquals("bar:baz", r.contents().get(0).asText().text()))
                .resourcesRead("file:///charlie/bar")
                .withErrorAssert(error -> assertEquals(JsonRpcErrorCodes.RESOURCE_NOT_FOUND, error.code()))
                .send()
                .resourcesRead("file:///delta/bar")
                .withErrorAssert(error -> assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, error.code()))
                .send()
                .resourcesRead("file:///echo/bar")
                .withErrorAssert(error -> assertEquals(JsonRpcErrorCodes.INTERNAL_ERROR, error.code()))
                .send()
                .resourcesRead("file:///foxtrot/test",
                        r -> assertEquals("{\"path\":\"test\"}", r.contents().get(0).asText().text()))
                .resourcesRead("file:///golf/123", r -> assertEquals("golf-123", r.contents().get(0).asText().text()))
                .thenAssertResults();
    }

}
