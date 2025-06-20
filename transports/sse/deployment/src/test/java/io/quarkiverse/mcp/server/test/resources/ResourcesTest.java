package io.quarkiverse.mcp.server.test.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ResourcesTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyResources.class, Checks.class));

    @Test
    public void testResources() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();

        client.when()
                .resourcesList(p -> {
                    assertEquals(4, p.size());
                    assertEquals("alpha", p.findByUri("file:///project/alpha").name());
                    assertEquals("bravo", p.findByUri("file:///project/bravo").name());
                    assertEquals("uni_alpha", p.findByUri("file:///project/uni_alpha").name());
                    assertEquals("uni_bravo", p.findByUri("file:///project/uni_bravo").name());
                })
                .resourcesRead("file:///project/alpha", r -> assertEquals("1", r.contents().get(0).asText().text()))
                .resourcesRead("file:///project/uni_alpha", r -> assertEquals("2", r.contents().get(0).asText().text()))
                .resourcesRead("file:///project/bravo", r -> assertEquals("3", r.contents().get(0).asText().text()))
                .resourcesRead("file:///project/uni_bravo", r -> assertEquals("4", r.contents().get(0).asText().text()))
                .thenAssertResults();
    }

}
