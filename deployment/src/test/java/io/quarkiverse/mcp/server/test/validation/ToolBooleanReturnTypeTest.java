package io.quarkiverse.mcp.server.test.validation;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkus.test.QuarkusUnitTest;

public class ToolBooleanReturnTypeTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(InvalidTool.class);
            })
            .setExpectedException(IllegalStateException.class);

    @Test
    public void test() {
        fail();
    }

    public static class InvalidTool {

        @Tool
        boolean foo() {
            return false;
        }

    }

}
