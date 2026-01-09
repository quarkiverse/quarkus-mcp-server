package io.quarkiverse.mcp.server.test.tools.guardrails;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ExecutionModel;
import io.quarkiverse.mcp.server.SupportedExecutionModels;
import io.quarkiverse.mcp.server.ToolInputGuardrail;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.QuarkusUnitTest;

public class ToolGuardrailsInvalidExecModelProgrammaticTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(EventLoopGuardrail.class);
            });

    @Inject
    ToolManager toolManager;

    @Test
    public void testFailure() {
        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> {
            toolManager.newTool("foo")
                    .setDescription("Foo")
                    // Blocking handler
                    .setHandler(ta -> ToolResponse.success("ok"))
                    .setInputGuardrails(List.of(EventLoopGuardrail.class))
                    .register();
        });
        assertTrue(iae.getMessage().endsWith(ExecutionModel.WORKER_THREAD.toString()), iae.getMessage());
    }

    @Singleton
    @SupportedExecutionModels(ExecutionModel.EVENT_LOOP)
    public static class EventLoopGuardrail implements ToolInputGuardrail {

    }

}
