package io.quarkiverse.mcp.server.test.filter;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolFilter;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.InitResult;
import io.quarkiverse.mcp.server.test.McpAssured.ServerCapability;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies the fail-open behavior of feature filters during {@code server/discover}: if a filter throws
 * a {@link RuntimeException} it is logged but the feature is still considered visible. See #997.
 */
public class ServerDiscoverFilterExceptionTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(MyFeatures.class, ThrowingFilter.class));

    @Test
    public void testFilterExceptionFailsOpen() {
        McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect(initResult -> {
                    // The filter throws, which is logged and treated as visible (fail-open),
                    // so the tools capability is still advertised
                    assertTrue(hasCapability(initResult, "tools"));
                });
    }

    private static boolean hasCapability(InitResult initResult, String name) {
        List<ServerCapability> capabilities = initResult.capabilities();
        return capabilities != null && capabilities.stream().map(ServerCapability::name).anyMatch(Predicate.isEqual(name));
    }

    public static class MyFeatures {

        @Tool
        String alpha() {
            return "alpha";
        }
    }

    // @Singleton added automatically
    public static class ThrowingFilter implements ToolFilter {

        @Override
        public boolean test(ToolInfo tool, McpConnection connection) {
            throw new IllegalStateException("Boom!");
        }
    }

}
