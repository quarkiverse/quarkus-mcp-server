package io.quarkiverse.mcp.server.test.validation;

import static io.quarkiverse.mcp.server.McpServer.DEFAULT;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests that two tools with the same name on overlapping servers are rejected at build time.
 */
public class DuplicateToolNameOverlappingServersTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(AlphaFeatures.class, BravoFeatures.class);
            })
            .overrideConfigKey("quarkus.mcp.server.support-multi-server-bindings", "true")
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public static class AlphaFeatures {

        @Tool
        @McpServer(DEFAULT)
        @McpServer("bravo")
        ToolResponse query() {
            return null;
        }
    }

    @McpServer("bravo")
    public static class BravoFeatures {

        @Tool
        ToolResponse query() {
            return null;
        }
    }
}
