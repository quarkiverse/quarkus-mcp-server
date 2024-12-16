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

public class CustomServerInfoTest extends McpServerTest {

    private static final String NAME = "Foo";
    private static final String VERSION = "1.0";

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(McpClient.class))
            .overrideConfigKey("quarkus.mcp-server.server-info.name", NAME)
            .overrideConfigKey("quarkus.mcp-server.server-info.version", VERSION);

    @Test
    public void testServerInfo() throws URISyntaxException {
        initClient(result -> {
            JsonObject serverInfo = result.getJsonObject("serverInfo");
            assertNotNull(serverInfo);
            assertEquals(NAME, serverInfo.getString("name"));
            assertEquals(VERSION, serverInfo.getString("version"));
        });
    }
}
