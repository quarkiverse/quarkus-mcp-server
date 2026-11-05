package io.quarkiverse.mcp.server.test.tools.guardrails;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ExecutionModel;
import io.quarkiverse.mcp.server.GlobalGuardrail;
import io.quarkiverse.mcp.server.SupportedExecutionModels;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolInputGuardrail;
import io.quarkus.test.QuarkusUnitTest;

public class GlobalGuardrailsInvalidExecModelProducerTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(MyTools.class, EventLoopOnlyGuardrail.class, GuardrailProducer.class);
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

    // Not a bean - the declared type of the producer is validated
    @SupportedExecutionModels(ExecutionModel.EVENT_LOOP)
    public static class EventLoopOnlyGuardrail implements ToolInputGuardrail {

    }

    @Singleton
    public static class GuardrailProducer {

        @GlobalGuardrail
        @Produces
        @Singleton
        EventLoopOnlyGuardrail guardrail() {
            return new EventLoopOnlyGuardrail();
        }

    }

}
