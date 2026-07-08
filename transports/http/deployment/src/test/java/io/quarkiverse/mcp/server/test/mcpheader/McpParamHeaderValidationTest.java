package io.quarkiverse.mcp.server.test.mcpheader;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.http.McpParamHeader;
import io.quarkus.test.QuarkusUnitTest;

public class McpParamHeaderValidationTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(InvalidTools.class))
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public static class InvalidTools {

        @Tool
        String unsupportedType(@McpParamHeader("Data") List<String> data) {
            return data.toString();
        }
    }
}
