package io.quarkiverse.mcp.server.test.extension;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpExtension;
import io.quarkiverse.mcp.server.McpExtensionMethod;
import io.quarkus.test.QuarkusExtensionTest;

public class ExtensionBuiltinMethodClashTest {

    @RegisterExtension
    public static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClasses(ClashExtension.class))
            // An extension method must not shadow a built-in MCP method
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    @McpExtension(id = "com.acme/clash")
    public static class ClashExtension {

        @McpExtensionMethod("tools/call")
        public String call() {
            return "nope";
        }
    }

}
