package io.quarkiverse.mcp.server.test.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusExtensionTest;

public class InvalidResourceUriTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(Checks.class, MyResources.class, NullResource.class));

    @Test
    public void testError() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .resourcesRead("file:///nonexistent")
                .withErrorAssert(e -> {
                    assertEquals(JsonRpcErrorCodes.RESOURCE_NOT_FOUND, e.code());
                    assertEquals("Resource not found: file:///nonexistent", e.message());
                    assertNotNull(e.data());
                    assertEquals("file:///nonexistent", e.data().getString("uri"));
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testNullResourceResponse() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .resourcesRead("file:///project/null_resource")
                .withErrorAssert(e -> {
                    assertEquals(JsonRpcErrorCodes.RESOURCE_NOT_FOUND, e.code());
                    assertEquals("Resource not found: file:///project/null_resource", e.message());
                    assertNotNull(e.data());
                    assertEquals("file:///project/null_resource", e.data().getString("uri"));
                })
                .send()
                .thenAssertResults();
    }

    public static class NullResource {

        @Resource(uri = "file:///project/null_resource")
        ResourceResponse nullResource(RequestUri uri) {
            return null;
        }
    }

}
