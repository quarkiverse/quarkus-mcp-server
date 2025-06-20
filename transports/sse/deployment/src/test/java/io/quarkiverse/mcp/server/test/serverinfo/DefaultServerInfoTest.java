package io.quarkiverse.mcp.server.test.serverinfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class DefaultServerInfoTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication();

    @Test
    public void testServerInfo() {
        McpAssured.newSseClient().build().connect(initResult -> {
            assertEquals("quarkus-mcp-server-sse-deployment", initResult.serverName());
            assertEquals(ConfigProvider.getConfig().getOptionalValue("quarkus.application.version", String.class).orElseThrow(),
                    initResult.serverVersion());
            assertTrue(initResult.capabilities().stream().anyMatch(c -> c.name().equals("logging")));
        });
    }
}
