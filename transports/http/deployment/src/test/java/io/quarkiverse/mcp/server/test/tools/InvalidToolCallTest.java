package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.runtime.Messages;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class InvalidToolCallTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication();

    @Test
    public void testMissingParams() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .message(Messages.newRequest(100, McpMethod.TOOLS_CALL.jsonRpcName()))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                })
                .send()
                .thenAssertResults();
    }

}
