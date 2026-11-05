package io.quarkiverse.mcp.server.test.tools.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import jakarta.enterprise.inject.Produces;
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

public class ToolGuardrailsProducerTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, PrefixGuardrail.class, GuardrailProducer.class));

    @Test
    public void testProducedGuardrail() {
        McpAssured.newConnectedStreamableClient()
                .when()
                .toolsCall("echo", Map.of("val", "hello"), toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("produced:hello", toolResponse.firstContent().asText().text());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @ToolGuardrails(output = PrefixGuardrail.class)
        @Tool
        String echo(String val) {
            return val;
        }

    }

    // Not a bean and no public no-args constructor - the producer must be used
    public static class PrefixGuardrail implements ToolOutputGuardrail {

        private final String prefix;

        PrefixGuardrail(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public void apply(ToolOutputContext context) {
            context.setResponse(ToolResponse.success(prefix + context.getResponse().firstContent().asText().text()));
        }

    }

    @Singleton
    public static class GuardrailProducer {

        @Produces
        @Singleton
        PrefixGuardrail prefixGuardrail() {
            return new PrefixGuardrail("produced:");
        }

    }

}
