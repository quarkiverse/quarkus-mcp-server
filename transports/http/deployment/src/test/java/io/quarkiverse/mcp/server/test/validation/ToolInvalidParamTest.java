package io.quarkiverse.mcp.server.test.validation;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.CompleteContext;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.test.QuarkusExtensionTest;

public class ToolInvalidParamTest {

    @RegisterExtension
    public static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(root -> {
                root.addClasses(InvalidParam.class);
            })
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public static class InvalidParam {

        @Tool
        String foo(CompleteContext completeContext) {
            return null;
        }

    }

}
