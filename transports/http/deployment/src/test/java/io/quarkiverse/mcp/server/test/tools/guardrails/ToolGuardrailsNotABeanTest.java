package io.quarkiverse.mcp.server.test.tools.guardrails;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolGuardrails;
import io.quarkiverse.mcp.server.ToolInputGuardrail;
import io.quarkus.test.QuarkusUnitTest;

public class ToolGuardrailsNotABeanTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(InvalidTools.class, InvalidGuardrail.class);
            })
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public static class InvalidTools {

        @ToolGuardrails(input = InvalidGuardrail.class)
        @Tool
        String foo() {
            throw new ToolCallException("boom");
        }

    }

    // neither a CDI bean, nor a no-args constructor
    public static class InvalidGuardrail implements ToolInputGuardrail {

        private InvalidGuardrail(String name) {
        }

    }

}
