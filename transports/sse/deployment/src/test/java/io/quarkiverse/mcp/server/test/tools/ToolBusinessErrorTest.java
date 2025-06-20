package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.WrapBusinessError;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;

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
                    assertEquals("Business error", r.content().get(0).asText().text());
                })
                .toolsCall("charlie", r -> {
                    assertTrue(r.isError());
                    assertEquals("java.lang.IllegalArgumentException: I am not ready!", r.content().get(0).asText().text());
                })
                .toolsCall("delta", Map.of("price", 10), r -> {
                    assertTrue(r.isError());
                    assertEquals("java.lang.NullPointerException: I am null!", r.content().get(0).asText().text());
                })
                .thenAssertResults();
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

    }

}
