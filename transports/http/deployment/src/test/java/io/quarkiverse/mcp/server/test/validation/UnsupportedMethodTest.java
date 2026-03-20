package io.quarkiverse.mcp.server.test.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.runtime.Messages;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class UnsupportedMethodTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication();

    @Test
    public void testError() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .message(Messages.newRequest(100, "alpha/bravo"))
                .withErrorAssert(e -> {
                    assertEquals(JsonRpcErrorCodes.METHOD_NOT_FOUND, e.code());
                    assertEquals("Unsupported method: alpha/bravo", e.message());
                })
                .send()
                .thenAssertResults();
    }

}
