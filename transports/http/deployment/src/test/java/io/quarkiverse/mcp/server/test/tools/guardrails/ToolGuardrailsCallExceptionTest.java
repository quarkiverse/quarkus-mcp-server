package io.quarkiverse.mcp.server.test.tools.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolGuardrails;
import io.quarkiverse.mcp.server.ToolOutputGuardrail;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ToolGuardrailsCallExceptionTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, AlwaysOk.class));

    @Test
    public void testOutput() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("alpha", Map.of("val", "ping"), toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("ok:error", toolResponse.firstContent().asText().text());
                })
                .toolsCall("bravo", Map.of("val", "ping"), toolResponse -> {
                    assertTrue(toolResponse.isError());
                    assertEquals("nok:ok:error", toolResponse.firstContent().asText().text());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @ToolGuardrails(output = AlwaysOk.class)
        @Tool
        String alpha(String val) {
            throw new ToolCallException("error");
        }

        @ToolGuardrails(output = { AlwaysOk.class, AlwaysNok.class })
        @Tool
        String bravo(String val) {
            throw new ToolCallException("error");
        }

    }

    @Singleton
    public static class AlwaysOk implements ToolOutputGuardrail {

        @Override
        public void apply(ToolOutputContext context) {
            if (context.getResponse().isError()) {
                context.setResponse(ToolResponse.success("ok:" + context.getResponse().firstContent().asText().text()));
            }
        }

    }

    @Singleton
    public static class AlwaysNok implements ToolOutputGuardrail {

        @Override
        public void apply(ToolOutputContext context) {
            if (!context.getResponse().isError()) {
                context.setResponse(ToolResponse.error("nok:" + context.getResponse().firstContent().asText().text()));
            }
        }

    }

}
