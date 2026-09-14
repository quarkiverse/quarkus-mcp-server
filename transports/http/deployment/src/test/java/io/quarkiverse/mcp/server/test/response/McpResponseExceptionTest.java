package io.quarkiverse.mcp.server.test.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

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
    public void testCustomResultException() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("customResult")
                .withRawAssert(response -> {
                    JsonObject result = response.getJsonObject("result");
                    assertNotNull(result);
                    assertEquals("custom_result", result.getString("resultType"));
                    assertEquals("Hello from extension", result.getString("message"));
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testImmutableResultException() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("immutableResult")
                .withRawAssert(response -> {
                    JsonObject result = response.getJsonObject("result");
                    assertNotNull(result);
                    // resultType is added by the framework without mutating the immutable payload returned by the exception
                    assertEquals("complete", result.getString("resultType"));
                    assertEquals("Immutable payload", result.getString("message"));
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testNullResultException() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("nullResult")
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INTERNAL_ERROR, error.code());
                    assertEquals("Internal error", error.message());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testThrowingResultException() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("throwingResult")
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INTERNAL_ERROR, error.code());
                    assertEquals("Internal error", error.message());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testCustomErrorException() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("customError")
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, error.code());
                    assertEquals("Custom error", error.message());
                    assertEquals("bar", error.data().getString("foo"));
                })
                .send()
                .thenAssertResults();
    }

    // Simulates a result-producing exception contributed by an MCP extension module
    static class CustomResultException extends McpResultException {

        CustomResultException() {
            super("Custom result");
        }

        @Override
        public JsonObject result() {
            return new JsonObject()
                    .put("resultType", "custom_result")
                    .put("message", "Hello from extension");
        }
    }

    // Simulates an extension that returns an immutable payload without a resultType; the framework must add the field
    // without mutating the returned instance
    static class ImmutableResultException extends McpResultException {

        ImmutableResultException() {
            super("Immutable result");
        }

        @Override
        public JsonObject result() {
            return new JsonObject(Map.of("message", "Immutable payload"));
        }
    }

    // Violates the result() contract by returning null; the framework must fail with an internal error, not an NPE
    static class NullResultException extends McpResultException {

        NullResultException() {
            super("Null result");
        }

        @Override
        public JsonObject result() {
            return null;
        }
    }

    // result() throws; the framework must fail with an internal error rather than letting it propagate
    static class ThrowingResultException extends McpResultException {

        ThrowingResultException() {
            super("Throwing result");
        }

        @Override
        public JsonObject result() {
            throw new IllegalStateException("Boom");
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
        String immutableResult() {
            throw new ImmutableResultException();
        }

        @Tool
        String nullResult() {
            throw new NullResultException();
        }

        @Tool
        String throwingResult() {
            throw new ThrowingResultException();
        }

        @Tool
        String customError() {
            throw new CustomErrorException();
        }

    }

}
