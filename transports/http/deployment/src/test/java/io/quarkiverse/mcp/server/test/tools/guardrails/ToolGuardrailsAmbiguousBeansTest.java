package io.quarkiverse.mcp.server.test.tools.guardrails;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolGuardrails;
import io.quarkiverse.mcp.server.ToolInputGuardrail;
import io.quarkus.test.QuarkusUnitTest;

public class ToolGuardrailsAmbiguousBeansTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(InvalidTools.class, Alpha.class, Bravo.class);
            })
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public static class InvalidTools {

        @ToolGuardrails(input = Alpha.class)
        @Tool
        String foo() {
            throw new ToolCallException("boom");
        }

    }

    @Singleton
    public static class Alpha implements ToolInputGuardrail {

    }

    @Singleton
    public static class Bravo extends Alpha {

    }

}
