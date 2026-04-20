package io.quarkiverse.mcp.server.test.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ResourceInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ResourcesTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyResources.class, Checks.class));

    @Inject
    ResourceManager resourceManager;

    @Test
    public void testResourceAnnotationsNoNullFields() {
        ResourceManager.ResourceInfo alpha = resourceManager.getResource("file:///project/alpha");
        assertNotNull(alpha);
        // alpha has @Annotations(audience = Role.USER, priority = 0.5) - lastModified is not set
        JsonObject json = alpha.asJson();
        // Encode and decode to verify the serialized form
        JsonObject decoded = new JsonObject(json.encode());
        JsonObject annotations = decoded.getJsonObject("annotations");
        assertNotNull(annotations);
        assertFalse(annotations.containsKey("lastModified"),
                "annotations must not contain 'lastModified' key when lastModified is not set");
        assertEquals("user", annotations.getString("audience"));
        assertEquals(0.5, annotations.getDouble("priority"));
    }

    @Test
    public void testResources() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();

        client.when()
                .resourcesList(p -> {
                    assertEquals(4, p.size());
                    ResourceInfo alpha = p.findByUri("file:///project/alpha");
                    assertEquals("alpha", alpha.name());
                    assertEquals("Alpha...", alpha.title());
                    assertEquals(15, alpha.size());
                    assertNotNull(alpha.annotations());
                    assertEquals(Role.USER, alpha.annotations().audience());
                    assertEquals(0.5, alpha.annotations().priority());
                    assertEquals("bravo", p.findByUri("file:///project/bravo").name());
                    assertEquals("uni_alpha", p.findByUri("file:///project/uni_alpha").name());
                    assertEquals("uni_bravo", p.findByUri("file:///project/uni_bravo").name());
                })
                // Verify the raw JSON response - null-valued optional fields must not be serialized
                .message(client.newRequest(McpAssured.RESOURCES_LIST))
                .withAssert(response -> {
                    JsonArray resources = response.getJsonObject("result").getJsonArray("resources");
                    for (int i = 0; i < resources.size(); i++) {
                        JsonObject resource = resources.getJsonObject(i);
                        JsonObject annotations = resource.getJsonObject("annotations");
                        if (annotations != null) {
                            for (String key : annotations.fieldNames()) {
                                assertNotNull(annotations.getValue(key),
                                        "annotations must not contain null value for key: " + key);
                            }
                        }
                    }
                })
                .send()
                .resourcesRead("file:///project/alpha", r -> assertEquals("1", r.contents().get(0).asText().text()))
                .resourcesRead("file:///project/uni_alpha", r -> assertEquals("2", r.contents().get(0).asText().text()))
                .resourcesRead("file:///project/bravo", r -> assertEquals("3", r.contents().get(0).asText().text()))
                .resourcesRead("file:///project/uni_bravo", r -> assertEquals("4", r.contents().get(0).asText().text()))
                .thenAssertResults();
    }

}
