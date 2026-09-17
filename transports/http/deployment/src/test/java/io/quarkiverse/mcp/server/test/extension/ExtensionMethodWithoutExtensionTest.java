package io.quarkiverse.mcp.server.test.extension;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpExtensionMethod;
import io.quarkus.test.QuarkusUnitTest;

public class ExtensionMethodWithoutExtensionTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(NotAnExtension.class))
            // @McpExtensionMethod requires the declaring class to be annotated with @McpExtension
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    // No @McpExtension annotation
    public static class NotAnExtension {

        @McpExtensionMethod("skills/get")
        public String get() {
            return "nope";
        }
    }

}
