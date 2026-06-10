package io.quarkiverse.mcp.server.test.tools.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolGuardrails;
import io.quarkiverse.mcp.server.ToolOutputGuardrail;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ToolOutputGuardrailNullResponseTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, NullResponseGuardrail.class));

    @Test
    public void testSetResponseNull() {
        McpAssured.newConnectedStreamableClient()
                .when()
                .toolsCall("echo", Map.of("val", "hello"), toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("NullPointerException caught", toolResponse.firstContent().asText().text());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @ToolGuardrails(output = NullResponseGuardrail.class)
        @Tool
        String echo(String val) {
            return val;
        }

    }

    @Singleton
    public static class NullResponseGuardrail implements ToolOutputGuardrail {

        @Override
        public void apply(ToolOutputContext context) {
            try {
                context.setResponse(null);
            } catch (NullPointerException e) {
                context.setResponse(ToolResponse.success("NullPointerException caught"));
            }
        }

    }

}
