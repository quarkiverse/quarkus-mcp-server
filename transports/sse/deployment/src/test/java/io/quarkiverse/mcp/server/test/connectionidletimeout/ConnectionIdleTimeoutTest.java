package io.quarkiverse.mcp.server.test.connectionidletimeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import jakarta.inject.Inject;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ConnectionIdleTimeoutTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication()
            // We expect that the client is fully initialized < 3s
            .overrideConfigKey("quarkus.mcp.server.connection-idle-timeout", "3s")
            .overrideConfigKey("quarkus.mcp.server.foo.connection-idle-timeout", "20m");

    @Inject
    ConnectionManager connectionManager;

    @Test
    public void testConnectionTimeout() throws InterruptedException {
        McpSseTestClient client = McpAssured.newConnectedSseClient();

        URI messageEndpoint = client.messageEndpoint();
        String messageEndpointStr = messageEndpoint.toString();
        String id = messageEndpointStr.substring(messageEndpointStr.lastIndexOf("/") + 1);

        // Wait until the connection is removed
        Awaitility.await().until(() -> !connectionManager.has(id));

        // Send a ping but expect the 400 status code
        client.when()
                .validateHttpResponse(response -> {
                    assertEquals(400, response.statusCode());
                })
                .ping()
                .send()
                .thenAssertResults();
    }
}
