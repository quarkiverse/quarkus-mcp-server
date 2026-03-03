package io.quarkiverse.mcp.server.test.serverinfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class DefaultServerInfoTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyFeatures.class));

    @Test
    public void testServerInfo() {
        McpAssured.newStreamableClient().build().connect(initResult -> {
            assertEquals("quarkus-mcp-server-http-deployment", initResult.serverName());
            assertEquals(ConfigProvider.getConfig().getOptionalValue("quarkus.application.version", String.class).orElseThrow(),
                    initResult.serverVersion());
            assertThat(initResult.capabilities()).filteredOn(c -> c.name().equals("tools") && c.properties().isEmpty())
                    .isNotEmpty();
            assertThat(initResult.capabilities())
                    .filteredOn(c -> c.name().equals("resources")
                            && c.properties().containsKey("subscribe")
                            && c.properties().containsKey("listChanged"))
                    .isNotEmpty();
            assertThat(initResult.capabilities()).filteredOn(c -> c.name().equals("logging"))
                    .isNotEmpty();
            assertThat(initResult.capabilities()).filteredOn(c -> c.name().equals("prompts"))
                    .isEmpty();
        });
    }

    public static class MyFeatures {

        @Inject
        ResourceManager resourceManager; // -> listChanged for resources

        @Tool
        String alpha() {
            return "alpha";
        }

        @Resource(uri = "file://test")
        byte[] bravo() {
            return "bravo".getBytes();
        }

    }
}
