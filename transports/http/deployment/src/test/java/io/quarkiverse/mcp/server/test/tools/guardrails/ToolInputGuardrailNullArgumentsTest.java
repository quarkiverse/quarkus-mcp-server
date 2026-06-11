package io.quarkiverse.mcp.server.test.tools.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolGuardrails;
import io.quarkiverse.mcp.server.ToolInputGuardrail;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ToolInputGuardrailNullArgumentsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, NullArgumentsGuardrail.class, EmptyArgumentsGuardrail.class));

    @Test
    public void testArguments() {
        McpAssured.newConnectedStreamableClient().when()
                .toolsCall("echo", Map.of("val", "hello"), toolResponse -> {
                    assertTrue(toolResponse.isError());
                    assertEquals("NullPointerException caught", toolResponse.firstContent().asText().text());
                })
                .toolsCall("noArgs", Map.of(), toolResponse -> {
                    assertTrue(toolResponse.isError());
                    assertEquals("Arguments empty", toolResponse.firstContent().asText().text());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @ToolGuardrails(input = NullArgumentsGuardrail.class)
        @Tool
        String echo(String val) {
            return val;
        }

        @ToolGuardrails(input = EmptyArgumentsGuardrail.class)
        @Tool
        String noArgs() {
            return "ok";
        }

    }

    @Singleton
    public static class NullArgumentsGuardrail implements ToolInputGuardrail {

        @Override
        public void apply(ToolInputContext context) {
            try {
                context.setArguments(null);
            } catch (NullPointerException e) {
                throw new ToolCallException("NullPointerException caught");
            }
        }

    }

    @Singleton
    public static class EmptyArgumentsGuardrail implements ToolInputGuardrail {

        @Override
        public void apply(ToolInputContext context) {
            if (context.getArguments().isEmpty()) {
                throw new ToolCallException("Arguments empty");
            }
            throw new ToolCallException("Arguments not empty");
        }

    }

}
