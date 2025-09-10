package io.quarkiverse.mcp.server.test.serverinfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class CustomServerInfoTest extends McpServerTest {

    private static final String NAME = "Foo";
    private static final String VERSION = "1.0";
    private static final String INSTRUCTIONS = """
            do that please
            and this on another line
            """;

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.mcp.server.server-info.name", NAME)
            .overrideConfigKey("quarkus.mcp.server.server-info.title", NAME.toUpperCase())
            .overrideConfigKey("quarkus.mcp.server.server-info.version", VERSION)
            .overrideConfigKey("quarkus.mcp.server.server-info.instructions", INSTRUCTIONS);

    @Test
    public void testServerInfo() {
        McpAssured.newSseClient().build().connect(initResult -> {
            assertEquals(NAME, initResult.serverName());
            assertEquals(NAME.toUpperCase(), initResult.serverTitle());
            assertEquals(VERSION, initResult.serverVersion());
            assertEquals(INSTRUCTIONS, initResult.instructions());
            assertTrue(initResult.capabilities().stream().anyMatch(c -> c.name().equals("logging")));
        });
    }
}
