package io.quarkiverse.mcp.server.test.mcpservers;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.test.QuarkusUnitTest;

public class InvalidServerNameTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(MyTools.class))
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public static class MyTools {

        @Tool
        @McpServer("bravo")
        String bravo() {
            return "2";
        }

    }

}
