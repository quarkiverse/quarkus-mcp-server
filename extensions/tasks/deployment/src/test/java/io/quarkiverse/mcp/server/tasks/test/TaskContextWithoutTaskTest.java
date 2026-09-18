package io.quarkiverse.mcp.server.tasks.test;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.tasks.TaskContext;
import io.quarkus.test.QuarkusUnitTest;

public class TaskContextWithoutTaskTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(MyTools.class))
            // A TaskContext parameter requires @Task
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public static class MyTools {

        @Tool(description = "Not a task")
        String foo(TaskContext task) {
            return "foo";
        }

    }

}
