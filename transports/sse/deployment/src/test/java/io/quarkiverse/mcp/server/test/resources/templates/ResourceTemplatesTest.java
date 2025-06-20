package io.quarkiverse.mcp.server.test.resources.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ResourceTemplatesTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTemplates.class, Checks.class));

    @Test
    public void testResourceTemplates() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .resourcesTemplatesList(p -> {
                    assertEquals(2, p.size());
                })
                .resourcesRead("file:///bar", r -> assertEquals("foo:bar", r.contents().get(0).asText().text()))
                .resourcesRead("file:///bar/baz", r -> assertEquals("bar:baz", r.contents().get(0).asText().text()))
                .thenAssertResults();
    }

}
