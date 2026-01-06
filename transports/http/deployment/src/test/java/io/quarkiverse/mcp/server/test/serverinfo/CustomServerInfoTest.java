package io.quarkiverse.mcp.server.test.serverinfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Icon.Theme;
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
    private static final String DESCRIPTION = "This is an MCP server!";
    private static final String URL = "https://github.com/quarkiverse/quarkus-mcp-server";

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.mcp.server.server-info.name", NAME)
            .overrideConfigKey("quarkus.mcp.server.server-info.title", NAME.toUpperCase())
            .overrideConfigKey("quarkus.mcp.server.server-info.version", VERSION)
            .overrideConfigKey("quarkus.mcp.server.server-info.description", DESCRIPTION)
            .overrideConfigKey("quarkus.mcp.server.server-info.website-url", URL)
            .overrideConfigKey("quarkus.mcp.server.server-info.icons[0].src", URL)
            .overrideConfigKey("quarkus.mcp.server.server-info.icons[0].mime-type", "image/png")
            .overrideConfigKey("quarkus.mcp.server.server-info.icons[0].theme", "dark")
            .overrideConfigKey("quarkus.mcp.server.server-info.icons[0].sizes[0]", "48x48")
            .overrideConfigKey("quarkus.mcp.server.server-info.icons[0].sizes[1]", "96x96")
            .overrideConfigKey("quarkus.mcp.server.server-info.instructions", INSTRUCTIONS);

    @Test
    public void testServerInfo() {
        McpAssured.newSseClient().build().connect(initResult -> {
            assertEquals(NAME, initResult.serverName());
            assertEquals(NAME.toUpperCase(), initResult.implementation().title());
            assertEquals(VERSION, initResult.serverVersion());
            assertEquals(INSTRUCTIONS, initResult.instructions());
            assertEquals(DESCRIPTION, initResult.implementation().description());
            assertEquals(URL, initResult.implementation().websiteUrl());
            assertEquals(1, initResult.implementation().icons().size());
            assertEquals(URL, initResult.implementation().icons().get(0).src());
            assertEquals("image/png", initResult.implementation().icons().get(0).mimeType());
            assertEquals(Theme.DARK, initResult.implementation().icons().get(0).theme());
            assertEquals(2, initResult.implementation().icons().get(0).sizes().size());
            assertTrue(initResult.implementation().icons().get(0).sizes().contains("48x48"));
            assertTrue(initResult.capabilities().stream().anyMatch(c -> c.name().equals("logging")));
        });
    }
}
