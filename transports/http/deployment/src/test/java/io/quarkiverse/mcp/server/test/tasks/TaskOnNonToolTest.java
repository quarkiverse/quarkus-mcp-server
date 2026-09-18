package io.quarkiverse.mcp.server.test.tasks;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.Task;
import io.quarkus.test.QuarkusUnitTest;

public class TaskOnNonToolTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(MyPrompts.class))
            // @Task may only be declared on a @Tool method
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public static class MyPrompts {

        @Task
        @Prompt(description = "Not a tool")
        PromptMessage foo() {
            return PromptMessage.withUserRole("foo");
        }

    }

}
