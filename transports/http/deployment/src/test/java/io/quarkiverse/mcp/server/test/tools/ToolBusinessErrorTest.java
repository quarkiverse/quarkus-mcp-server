package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.WrapBusinessError;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

public class ToolBusinessErrorTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testError() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .toolsCall("bravo", Map.of("price", 10), r -> {
                    assertTrue(r.isError());
                    assertEquals("Business error", r.firstContent().asText().text());
                })
                .toolsCall("charlie", r -> {
                    assertTrue(r.isError());
                    assertEquals("java.lang.IllegalArgumentException: I am not ready!", r.firstContent().asText().text());
                })
                .toolsCall("delta", Map.of("price", 10), r -> {
                    assertTrue(r.isError());
                    assertEquals("java.lang.NullPointerException: I am null!", r.firstContent().asText().text());
                })
                .toolsCall("echo")
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INTERNAL_ERROR, error.code());
                    assertEquals("Testik", error.message());
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testControlFlowExceptionsNeverWrapped() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "foxtrot"));
        client.sendAndForget(request);

        Awaitility.await()
                .atMost(2, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertEquals(1, client.snapshot().responses().size());
                });
    }

    public static class MyTools {

        @Tool
        TextContent bravo(int price) {
            throw new ToolCallException("Business error");
        }

        @WrapBusinessError
        @Tool
        String charlie() {
            throw new IllegalArgumentException("I am not ready!");
        }

        @WrapBusinessError(NullPointerException.class)
        @Tool
        Uni<String> delta() {
            return Uni.createFrom().failure(new NullPointerException("I am null!"));
        }

        @WrapBusinessError(unless = McpException.class)
        @Tool
        String echo() {
            throw new McpException("Testik", JsonRpcErrorCodes.INTERNAL_ERROR);
        }

        @WrapBusinessError(value = Exception.class, unless = ToolCallException.class)
        @Tool
        String foxtrot() {
            throw new Cancellation.OperationCancellationException();
        }

    }

}
