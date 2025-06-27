package io.quarkiverse.mcp.server.test.mcpservers;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.test.QuarkusUnitTest;

public class MutipleServersDuplicitRootPathTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.sse.root-path", "/foo/mcp")
            .overrideConfigKey("quarkus.mcp.server.bravo.sse.root-path", "/foo/mcp")
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public static class MyTools {

        @Tool
        String alpha() {
            return "1";
        }

        @Tool
        @McpServer("bravo")
        String bravo() {
            return "2";
        }

    }

}
