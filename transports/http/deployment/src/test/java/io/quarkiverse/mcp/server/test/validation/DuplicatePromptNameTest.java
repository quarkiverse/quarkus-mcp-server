package io.quarkiverse.mcp.server.test.validation;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkus.test.QuarkusExtensionTest;

public class DuplicatePromptNameTest {

    @RegisterExtension
    public static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(root -> {
                root.addClasses(InvalidPrompts.class);
            })
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public static class InvalidPrompts {

        @Prompt
        PromptResponse foo() {
            return null;
        }

        @Prompt(name = "foo")
        PromptResponse foos() {
            return null;
        }

    }

}
