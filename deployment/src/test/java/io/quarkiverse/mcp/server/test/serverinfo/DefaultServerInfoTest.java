package io.quarkiverse.mcp.server.test.serverinfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URISyntaxException;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.McpClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class DefaultServerInfoTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(McpClient.class));

    @Test
    public void testServerInfo() throws URISyntaxException {
        initClient(result -> {
            JsonObject serverInfo = result.getJsonObject("serverInfo");
            assertNotNull(serverInfo);
            assertEquals("quarkus-mcp-server-deployment", serverInfo.getString("name"));
            assertEquals(ConfigProvider.getConfig().getOptionalValue("quarkus.application.version", String.class).orElseThrow(),
                    serverInfo.getString("version"));
        });
    }
}
