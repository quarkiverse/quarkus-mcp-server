package io.quarkiverse.mcp.server.test.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class InvalidResourceUriTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(Checks.class, MyResources.class));

    @Test
    public void testError() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .resourcesRead("file:///nonexistent")
                .withErrorAssert(e -> {
                    assertEquals(JsonRpcErrorCodes.RESOURCE_NOT_FOUND, e.code());
                    assertEquals("Resource not found: file:///nonexistent", e.message());
                })
                .send()
                .thenAssertResults();
    }

}
