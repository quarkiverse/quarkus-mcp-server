package io.quarkiverse.mcp.server.test.extension;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpExtension;
import io.quarkiverse.mcp.server.McpExtensionMethod;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkus.test.QuarkusUnitTest;

public class ExtensionMethodServerBindingTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(BadExtension.class))
            // @McpServer must be declared on the @McpExtension class, not on an individual extension method
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    @McpExtension(id = "com.acme/bad")
    public static class BadExtension {

        @McpServer("bravo")
        @McpExtensionMethod("skills/get")
        public String get() {
            return "nope";
        }
    }

}
