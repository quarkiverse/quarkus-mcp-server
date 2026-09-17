package io.quarkiverse.mcp.server.test.extension;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpExtension;
import io.quarkus.test.QuarkusUnitTest;

public class ExtensionInvalidIdTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(NoPrefixExtension.class))
            // The extension id must include a mandatory prefix per the spec
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    @McpExtension(id = "skills")
    public static class NoPrefixExtension {
    }

}
