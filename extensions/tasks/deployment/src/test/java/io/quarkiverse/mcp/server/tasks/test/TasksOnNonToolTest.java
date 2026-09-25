package io.quarkiverse.mcp.server.tasks.test;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.tasks.Tasks;
import io.quarkus.test.QuarkusUnitTest;

public class TasksOnNonToolTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(MyPrompts.class))
            // Tasks may only be injected into a tool method
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public static class MyPrompts {

        @Prompt(description = "Not a tool")
        PromptMessage foo(Tasks tasks) {
            return PromptMessage.withUserRole("foo");
        }

    }

}
