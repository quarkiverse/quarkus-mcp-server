package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class DuplicateToolArgNameTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(MyTools.class))
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public static class MyTools {

        @Tool
        String findProduct(
                @ToolArg(name = "code") String skuCode,
                @ToolArg(name = "code") String barCode) {
            return skuCode + barCode;
        }
    }
}
