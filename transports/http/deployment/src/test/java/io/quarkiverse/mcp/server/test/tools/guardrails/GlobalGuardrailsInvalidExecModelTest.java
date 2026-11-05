package io.quarkiverse.mcp.server.test.tools.guardrails;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ExecutionModel;
import io.quarkiverse.mcp.server.GlobalGuardrail;
import io.quarkiverse.mcp.server.SupportedExecutionModels;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolOutputGuardrail;
import io.quarkus.test.QuarkusUnitTest;

public class GlobalGuardrailsInvalidExecModelTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(MyTools.class, WorkerOnlyGuardrail.class);
            })
            .assertException(t -> {
                Throwable root = t;
                while (root.getCause() != null) {
                    root = root.getCause();
                }
                if (!(root instanceof IllegalStateException) || !root.getMessage().contains(
                        "must support all execution models")) {
                    fail(t);
                }
            });

    @Test
    public void test() {
        fail();
    }

    public static class MyTools {

        @Tool
        String foo() {
            return "foo";
        }

    }

    // A global guardrail must support all execution models
    @GlobalGuardrail
    @SupportedExecutionModels({ ExecutionModel.WORKER_THREAD, ExecutionModel.VIRTUAL_THREAD })
    public static class WorkerOnlyGuardrail implements ToolOutputGuardrail {

    }

}
