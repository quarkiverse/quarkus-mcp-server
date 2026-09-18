package io.quarkiverse.mcp.server.test.extension;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpExtension;
import io.quarkiverse.mcp.server.McpExtensionMethod;
import io.quarkus.test.QuarkusUnitTest;

public class ExtensionDuplicateMethodTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(DuplicateMethodExtension.class))
            // Two extension methods with the same name bound to the same (default) server
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    @McpExtension(id = "com.acme/dup")
    public static class DuplicateMethodExtension {

        @McpExtensionMethod("skills/get")
        public String a() {
            return "a";
        }

        @McpExtensionMethod("skills/get")
        public String b() {
            return "b";
        }
    }

}
