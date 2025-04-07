package io.quarkiverse.mcp.server.test.rootpath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class CustomRootPathSlashTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.mcp.server.sse.root-path", "/");

    @Override
    protected String sseRootPath() {
        return "/";
    }

    @Test
    public void testServerInfo() {
        initClient(result -> {
            JsonObject serverInfo = result.getJsonObject("serverInfo");
            assertNotNull(serverInfo);
            assertEquals("quarkus-mcp-server-sse-deployment", serverInfo.getString("name"));
            assertEquals(ConfigProvider.getConfig().getOptionalValue("quarkus.application.version", String.class).orElseThrow(),
                    serverInfo.getString("version"));
            JsonObject capabilities = result.getJsonObject("capabilities");
            assertTrue(capabilities.containsKey("logging"));
        });
    }
}
