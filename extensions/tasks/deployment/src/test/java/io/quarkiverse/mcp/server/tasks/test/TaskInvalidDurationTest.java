package io.quarkiverse.mcp.server.tasks.test;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.tasks.Task;
import io.quarkus.test.QuarkusUnitTest;

public class TaskInvalidDurationTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(MyTools.class))
            // The pollInterval is not a valid duration
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public static class MyTools {

        @Task(pollInterval = "soon")
        @Tool(description = "Invalid")
        String foo() {
            return "foo";
        }

    }

}
