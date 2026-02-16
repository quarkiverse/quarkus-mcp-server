package io.quarkiverse.mcp.server.test.serverinfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Icon;
import io.quarkiverse.mcp.server.Icon.Theme;
import io.quarkiverse.mcp.server.Implementation;
import io.quarkiverse.mcp.server.InitialResponseInfo;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class InitialResponseInfoTest extends McpServerTest {

    private static final String NAME = "Foo";
    private static final String VERSION = "1.0";
    private static final String INSTRUCTIONS = """
            do that please
            and this on another line
            """;
    private static final String DESCRIPTION = "This is an MCP server!";
    private static final String URL = "https://github.com/quarkiverse/quarkus-mcp-server";

    private static final String ALPHA_NAME = "Foo";
    private static final String ALPHA_VERSION = "1.1";
    private static final String ALPHA_INSTRUCTIONS = "Use me well";
    private static final String ALPHA_URL = "http://foo.com/icon.png";

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(2000)
            .withApplicationRoot(root -> root.addClasses(DefaultInitResponseInfo.class, AlphaInitResponseInfo.class))
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
            .overrideConfigKey("quarkus.mcp.server.alpha.http.root-path", "/alpha/mcp")
            .overrideConfigKey("quarkus.mcp.server.alpha.server-info.instructions", ALPHA_INSTRUCTIONS);

    @Test
    public void testServerInfo() {
        // Default server
        McpAssured.newStreamableClient().build().connect(initResult -> {
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
            assertNotNull(initResult.meta());
            assertEquals("bar", initResult.meta().getString("foo"));
        }).disconnect();

        // "alpha" server
        McpAssured.newStreamableClient()
                .setMcpPath("/alpha/mcp")
                .build()
                .connect(initResult -> {
                    assertEquals(ALPHA_NAME, initResult.serverName());
                    assertNull(initResult.implementation().title());
                    assertEquals(ALPHA_VERSION, initResult.serverVersion());
                    assertEquals(ALPHA_INSTRUCTIONS, initResult.instructions());
                    assertNull(initResult.implementation().websiteUrl());
                    assertEquals(1, initResult.implementation().icons().size());
                    assertEquals(ALPHA_URL, initResult.implementation().icons().get(0).src());
                    assertNull(initResult.implementation().icons().get(0).theme());
                    assertTrue(initResult.capabilities().stream().anyMatch(c -> c.name().equals("logging")));
                    assertNotNull(initResult.meta());
                    assertEquals("alpha", initResult.meta().getString("foo"));
                }).disconnect();
    }

    @Priority(100)
    @Singleton
    public static class DefaultInitResponseInfo implements InitialResponseInfo {

        @Override
        public Optional<String> instructions(String serverName) {
            return McpServer.DEFAULT.equals(serverName) ? Optional.of(INSTRUCTIONS) : Optional.empty();
        }

        @Override
        public Optional<Map<MetaKey, Object>> meta(String serverName) {
            return McpServer.DEFAULT.equals(serverName) ? Optional.of(Map.of(MetaKey.of("foo"), "bar")) : Optional.empty();
        }

    }

    @Singleton
    @Priority(10)
    public static class AlphaInitResponseInfo implements InitialResponseInfo {

        @Override
        public Optional<Map<MetaKey, Object>> meta(String serverName) {
            return Optional.of(Map.of(MetaKey.of("foo"), "alpha"));
        }

        @Override
        public Optional<Implementation> implementation(String serverName) {
            if ("alpha".equals(serverName)) {
                Implementation impl = new Implementation(ALPHA_NAME, ALPHA_VERSION, null, List.of(new Icon(ALPHA_URL, null)),
                        null, null);
                return Optional.of(impl);
            }
            return Optional.empty();
        }

    }
}
