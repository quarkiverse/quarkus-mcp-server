package io.quarkiverse.mcp.server.hibernate.validator.test;

import static io.quarkiverse.mcp.server.McpServer.DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.QuarkusUnitTest;

public class ResourcesConstraintViolationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyResources.class));

    @Inject
    MyResources resources;

    @Test
    public void testError() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .resourcesRead("file:///mkouba@redhat.com", resourceResponse -> {
                    assertEquals("to:mkouba@redhat.com", resourceResponse.firstContents().asText().text());
                })
                .resourcesRead("file:///noanemail")
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, error.code());
                    assertEquals("bravo.to: must be a well-formed email address", error.message());
                })
                .send()
                .thenAssertResults();

        assertThrows(McpException.class, () -> resources.bravo("also not an email", null));
        assertThrows(ConstraintViolationException.class, () -> resources.nonResourceBravo("foo	"));
    }

    @McpServer(DEFAULT)
    public static class MyResources {

        @ResourceTemplate(uriTemplate = "file:///{to}")
        TextResourceContents bravo(@Email String to, RequestUri uri) {
            return TextResourceContents.create(uri.value(), "to:" + to);
        }

        String nonResourceBravo(@Email String to) {
            return "to" + to;
        }

    }

}
