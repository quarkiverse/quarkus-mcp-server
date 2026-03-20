package io.quarkiverse.mcp.server.schema.validator.test.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.schema.validator.test.McpServerTest;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ResourcesSchemaValidationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyResources.class));

    @Test
    public void testResources() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .resourcesList(page -> {
                    assertEquals(1, page.size());
                    assertEquals("file:///test/alpha", page.resources().get(0).uri());
                })
                .resourcesRead("file:///test/alpha", r -> {
                    assertEquals("alpha-content", r.contents().get(0).asText().text());
                })
                // Send a resources/list request with invalid cursor type (must be string)
                .message(client.newRequest("resources/list").put("params", new JsonObject().put("cursor", 123)))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertTrue(error.message().startsWith("Schema validation failed"));
                })
                .send()
                // Send a resources/read request without the required "params" field
                .message(client.newRequest("resources/read"))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertTrue(error.message().startsWith("Schema validation failed"));
                })
                .send()
                .thenAssertResults();
    }

    public static class MyResources {

        @Resource(uri = "file:///test/alpha")
        ResourceResponse alpha(RequestUri uri) {
            return new ResourceResponse(List.of(new TextResourceContents(uri.value(), "alpha-content", null)));
        }

    }

}
