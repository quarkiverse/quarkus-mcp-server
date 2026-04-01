package io.quarkiverse.mcp.server.test.resources;

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
import io.vertx.core.json.JsonObject;

public class InvalidResourceSubscribeTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication();

    @Test
    public void testSubscribeToNonExistentResource() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .message(Messages.newRequest(100, McpMethod.RESOURCES_SUBSCRIBE.jsonRpcName(),
                        new JsonObject().put("uri", "file:///nonexistent")))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.RESOURCE_NOT_FOUND, error.code());
                    assertEquals("Resource not found: file:///nonexistent", error.message());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testUnsubscribeFromNonExistentResource() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .message(Messages.newRequest(101, McpMethod.RESOURCES_UNSUBSCRIBE.jsonRpcName(),
                        new JsonObject().put("uri", "file:///nonexistent")))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.RESOURCE_NOT_FOUND, error.code());
                    assertEquals("Resource not found: file:///nonexistent", error.message());
                })
                .send()
                .thenAssertResults();
    }

}
