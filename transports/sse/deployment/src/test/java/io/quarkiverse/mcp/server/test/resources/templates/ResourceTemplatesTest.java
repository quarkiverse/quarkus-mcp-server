package io.quarkiverse.mcp.server.test.resources.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ResourceTemplateInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ResourceTemplatesTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTemplates.class, Checks.class, AlwaysError.class, AlwaysErrorInterceptor.class));

    @Test
    public void testResourceTemplates() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .resourcesTemplatesList(p -> {
                    assertEquals(5, p.size());
                    ResourceTemplateInfo alpha = p.findByUriTemplate("file:///{path}");
                    assertEquals("Alpha...", alpha.title());
                    assertNotNull(alpha.annotations());
                    assertEquals(Role.USER, alpha.annotations().audience());
                    assertEquals(0.5, alpha.annotations().priority());
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
                .thenAssertResults();
    }

}
