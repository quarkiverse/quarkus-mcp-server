package io.quarkiverse.mcp.server.test.validation;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkus.test.QuarkusUnitTest;

public class ToolNamePatternDeclarativeTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(InvalidTools.class);
            })
            .overrideConfigKey("quarkus.mcp.server.tools.name-pattern",
                    Tool.SPEC_NAME_PATTERN)
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public static class InvalidTools {

        @Tool(name = "tool with spaces")
        String foo() {
            throw new ToolCallException("boom");
        }

    }

}
