package io.quarkiverse.mcp.server.test.serverinfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.McpClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class DefaultServerInfoTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(McpClient.class));

    @Test
    public void testServerInfo() throws URISyntaxException {
        initClient(result -> {
            JsonObject serverInfo = result.getJsonObject("serverInfo");
            assertNotNull(serverInfo);
            assertEquals("quarkus-mcp-server-deployment", serverInfo.getString("name"));
            assertEquals("999-SNAPSHOT", serverInfo.getString("version"));
        });
    }
}