package io.quarkiverse.mcp.server.test.validation;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkus.test.QuarkusUnitTest;

public class ToolLongNameTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(InvalidParam.class);
            })
            .overrideConfigKey("quarkus.mcp.server.tools.name-max-length", "10")
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public static class InvalidParam {

        @Tool(name = "Too long name of a tool should result in ISE!")
        String foo() {
            throw new ToolCallException("boom");
        }

    }

}
