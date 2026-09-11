package io.quarkiverse.mcp.server.test.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpResultException;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

/**
 * Verifies that custom {@link io.quarkiverse.mcp.server.McpResponseException} subtypes are automatically
 * converted to a JSON-RPC response.
 */
public class McpResponseExceptionTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testCustomResultException() throws InterruptedException {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject().put("name", "customResult"));
        client.sendAndForget(request);

        JsonObject response = client.waitForResponse(request);
        JsonObject result = response.getJsonObject("result");
        assertNotNull(result);
        assertEquals("custom_result", result.getString("resultType"));
        assertEquals("Hello from extension", result.getString("message"));
    }

    @Test
    public void testCustomErrorException() throws InterruptedException {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject().put("name", "customError"));
        client.sendAndForget(request);

        JsonObject response = client.waitForResponse(request);
        JsonObject error = response.getJsonObject("error");
        assertNotNull(error);
        assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, error.getInteger("code"));
        assertEquals("Custom error", error.getString("message"));
        assertEquals("bar", error.getJsonObject("data").getString("foo"));
    }

    // Simulates a result-producing exception contributed by an MCP extension module
    static class CustomResultException extends McpResultException {

        CustomResultException() {
            super("Custom result");
        }

        @Override
        public Object result() {
            return new JsonObject()
                    .put("resultType", "custom_result")
                    .put("message", "Hello from extension");
        }
    }

    // Simulates an error-producing exception contributed by an MCP extension module
    static class CustomErrorException extends McpException {

        CustomErrorException() {
            super("Custom error", JsonRpcErrorCodes.INVALID_PARAMS, new JsonObject().put("foo", "bar"));
        }
    }

    @Singleton
    public static class MyTools {

        @Tool
        String customResult() {
            throw new CustomResultException();
        }

        @Tool
        String customError() {
            throw new CustomErrorException();
        }

    }

}
