package io.quarkiverse.mcp.server.schema.validator.test.resourcetemplates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.schema.validator.test.McpServerTest;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ResourceTemplatesSchemaValidationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyResourceTemplates.class));

    @Test
    public void testResourceTemplatesList() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                // Valid resources/templates/list
                .resourcesTemplatesList(page -> {
                    assertEquals(1, page.size());
                    assertEquals("file:///{path}", page.templates().get(0).uriTemplate());
                })
                // Send a resources/templates/list request with invalid params
                .message(client.newRequest("resources/templates/list")
                        .put("params", new JsonObject().put("cursor", 123)))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertTrue(error.message().startsWith("Schema validation failed"));
                })
                .send()
                .thenAssertResults();
    }

    public static class MyResourceTemplates {

        @ResourceTemplate(uriTemplate = "file:///{path}")
        TextResourceContents fileTemplate(String path, RequestUri uri) {
            return TextResourceContents.create(uri.value(), "content:" + path);
        }
    }
}
