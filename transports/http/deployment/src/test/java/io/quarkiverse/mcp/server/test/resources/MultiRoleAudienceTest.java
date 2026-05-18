package io.quarkiverse.mcp.server.test.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ResourceInfo;
import io.quarkiverse.mcp.server.test.McpAssured.ResourceTemplateInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class MultiRoleAudienceTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MultiRoleAudienceResources.class,
                            MultiRoleAudienceResources.ProgrammaticRegistrar.class));

    @Inject
    ResourceManager resourceManager;

    @Inject
    ResourceTemplateManager resourceTemplateManager;

    @Inject
    MultiRoleAudienceResources.ProgrammaticRegistrar registrar;

    @Test
    public void testMultiRoleResource() {
        // Verify via ResourceManager API
        ResourceManager.ResourceInfo alphaInfo = resourceManager.getResource("file:///multi/alpha", McpServer.DEFAULT);
        assertNotNull(alphaInfo);
        Content.Annotations annotations = alphaInfo.annotations().orElseThrow();
        assertEquals(List.of(Role.USER, Role.ASSISTANT), annotations.audience());
        assertEquals(0.6, annotations.priority());

        // Verify JSON serialization
        JsonObject json = alphaInfo.asJson();
        JsonObject decoded = new JsonObject(json.encode());
        JsonObject annJson = decoded.getJsonObject("annotations");
        assertNotNull(annJson);
        JsonArray audience = annJson.getJsonArray("audience");
        assertEquals(2, audience.size());
        assertEquals("user", audience.getString(0));
        assertEquals("assistant", audience.getString(1));
        assertEquals(0.6, annJson.getDouble("priority"));

        // Verify via MCP client
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .resourcesList(p -> {
                    ResourceInfo alpha = p.findByUri("file:///multi/alpha");
                    assertNotNull(alpha.annotations());
                    assertEquals(List.of(Role.USER, Role.ASSISTANT), alpha.annotations().audience());
                    assertEquals(0.6, alpha.annotations().priority());
                })
                .resourcesRead("file:///multi/alpha", r -> assertEquals("alpha", r.contents().get(0).asText().text()))
                .thenAssertResults();
    }

    @Test
    public void testMultiRoleResourceTemplate() {
        // Verify via ResourceTemplateManager API
        ResourceTemplateManager.ResourceTemplateInfo templateInfo = resourceTemplateManager
                .getResourceTemplate("template", McpServer.DEFAULT);
        assertNotNull(templateInfo);
        Content.Annotations annotations = templateInfo.annotations().orElseThrow();
        assertEquals(List.of(Role.ASSISTANT, Role.USER), annotations.audience());
        assertEquals(0.7, annotations.priority());

        // Verify via MCP client
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .resourcesTemplatesList(p -> {
                    ResourceTemplateInfo template = p.findByUriTemplate("file:///multi/template/{id}");
                    assertNotNull(template.annotations());
                    assertEquals(List.of(Role.ASSISTANT, Role.USER), template.annotations().audience());
                    assertEquals(0.7, template.annotations().priority());
                })
                .resourcesRead("file:///multi/template/42",
                        r -> assertEquals("template-42", r.contents().get(0).asText().text()))
                .thenAssertResults();
    }

    @Test
    public void testMultiRoleProgrammaticResource() {
        registrar.registerResource();

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .resourcesList(p -> {
                    ResourceInfo programmatic = p.findByUri("file:///multi/programmatic");
                    assertNotNull(programmatic.annotations());
                    assertEquals(List.of(Role.ASSISTANT, Role.USER), programmatic.annotations().audience());
                    assertEquals(0.8, programmatic.annotations().priority());
                })
                .resourcesRead("file:///multi/programmatic",
                        r -> assertEquals("programmatic", r.contents().get(0).asText().text()))
                .thenAssertResults();
    }

    @Test
    public void testMultiRoleProgrammaticResourceTemplate() {
        registrar.registerResourceTemplate();

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .resourcesTemplatesList(p -> {
                    ResourceTemplateInfo template = p
                            .findByUriTemplate("file:///multi/programmatic_template/{name}");
                    assertNotNull(template.annotations());
                    assertEquals(List.of(Role.USER, Role.ASSISTANT), template.annotations().audience());
                    assertEquals(0.9, template.annotations().priority());
                })
                .resourcesRead("file:///multi/programmatic_template/hello",
                        r -> assertEquals("hello", r.contents().get(0).asText().text()))
                .thenAssertResults();
    }
}
