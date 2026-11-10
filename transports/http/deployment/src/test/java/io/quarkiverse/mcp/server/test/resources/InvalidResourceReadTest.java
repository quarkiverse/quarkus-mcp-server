package io.quarkiverse.mcp.server.test.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.runtime.Messages;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class InvalidResourceReadTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication();

    @Test
    public void testMissingParams() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .message(Messages.newRequest(100, McpMethod.RESOURCES_READ.jsonRpcName()))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testUnknownResourceStateful() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .resourcesRead("file:///nothing")
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.RESOURCE_NOT_FOUND, error.code());
                    assertEquals("Resource not found: file:///nothing", error.message());
                    assertNotNull(error.data());
                    assertEquals("file:///nothing", error.data().getString("uri"));
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testUnknownResourceStateless() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setProtocolVersion(McpProtocolVersion.V_2026_07_28)
                .build()
                .connect();
        client.when()
                .resourcesRead("file:///nothing")
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, error.code());
                    assertEquals("Resource not found: file:///nothing", error.message());
                    assertNotNull(error.data());
                    assertEquals("file:///nothing", error.data().getString("uri"));
                })
                .send()
                .thenAssertResults();
    }

}
