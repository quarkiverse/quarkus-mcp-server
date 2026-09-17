package io.quarkiverse.mcp.server.test.extension;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpExtension;
import io.quarkus.test.QuarkusUnitTest;

public class ExtensionDuplicateIdTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(FirstExtension.class, SecondExtension.class))
            // Two extensions share the same id and overlapping (default) server bindings
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    @McpExtension(id = "com.acme/same")
    public static class FirstExtension {
    }

    @McpExtension(id = "com.acme/same")
    public static class SecondExtension {
    }

}
