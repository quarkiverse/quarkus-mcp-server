package io.quarkiverse.mcp.server.stdio.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * If stdio is the only transport then the connection idle timeout is disabled by default.
 */
public class ConnectionIdleTimeoutStdioOnlyTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            // Do not read stdin, otherwise the app would exit on EOF
            .overrideConfigKey("quarkus.mcp.server.stdio.initialization-enabled", "false");

    @Inject
    McpServersRuntimeConfig serversConfig;

    @Test
    public void testIdleTimeoutDisabled() {
        assertEquals(Duration.ZERO, serversConfig.servers().get(McpServer.DEFAULT).connectionIdleTimeout());
    }

}
