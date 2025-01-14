package io.quarkiverse.mcp.server.test.validation;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkus.test.QuarkusUnitTest;

public class PromptInvalidParamTypeTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(InvalidPrompt.class);
            })
            .setExpectedException(IllegalStateException.class);

    @Test
    public void test() {
        fail();
    }

    public static class InvalidPrompt {

        @Prompt
        PromptMessage foo(int age) {
            return null;
        }

    }

}
