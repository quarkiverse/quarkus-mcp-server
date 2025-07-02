package io.quarkiverse.mcp.server.test.ping;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class AutoPingIntervalTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.mcp.server.auto-ping-interval", "1s");

    @Test
    public void testPing() {
        McpSseTestClient client = McpAssured.newSseClient()
                .setAutoPong(true)
                .build()
                .connect();
        // Wait for the first ping from the server
        Awaitility.await()
                .until(() -> client.snapshot().requests().stream().anyMatch(r -> "ping".equals(r.getString("method"))));
    }
}
