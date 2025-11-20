package io.quarkiverse.mcp.server.test.complete;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ResourceTemplateCompleteTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyResourceTemplates.class));

    @Test
    public void testCompletion() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client
                .when()
                .resourceTemplateComplete("file:///{foo}/{bar}", "foo", "Ja", completionResponse -> {
                    assertEquals(1, completionResponse.values().size());
                    assertEquals("Jachym", completionResponse.values().get(0));
                })
                .resourceTemplateComplete("file:///{foo}/{bar}", "bar", "Ja", completionResponse -> {
                    assertEquals(1, completionResponse.values().size());
                    assertEquals("_bar", completionResponse.values().get(0));
                })
                .thenAssertResults();
    }
}
