package io.quarkiverse.mcp.server.test.tools.guardrails;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ExecutionModel;
import io.quarkiverse.mcp.server.SupportedExecutionModels;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolGuardrails;
import io.quarkiverse.mcp.server.ToolInputGuardrail;
import io.quarkus.test.QuarkusUnitTest;

public class ToolGuardrailsInvalidExecModelTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(InvalidTools.class, EventLoopGuardrail.class);
            })
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public static class InvalidTools {

        @ToolGuardrails(input = EventLoopGuardrail.class)
        @Tool
        String foo() {
            throw new ToolCallException("boom");
        }

    }

    @Singleton
    @SupportedExecutionModels(ExecutionModel.EVENT_LOOP)
    public static class EventLoopGuardrail implements ToolInputGuardrail {

    }

}
