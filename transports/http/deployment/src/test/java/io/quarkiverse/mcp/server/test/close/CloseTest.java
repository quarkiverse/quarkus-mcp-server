package io.quarkiverse.mcp.server.test.close;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import jakarta.inject.Inject;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.McpMessageHandler;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class CloseTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication();

    @Inject
    ConnectionManager connectionManager;

    @Test
    public void testCloseMessage() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();

        URI messageEndpoint = client.messageEndpoint();
        String messageEndpointStr = messageEndpoint.toString();
        String id = messageEndpointStr.substring(messageEndpointStr.lastIndexOf("/") + 1);

        // Send "q/close"
        client.when()
                .message(client.newRequest(McpMessageHandler.Q_CLOSE))
                .send()
                .thenAssertResults();

        // Wait until the connection is removed
        Awaitility.await().until(() -> !connectionManager.has(id));

        // Send a ping but expect the 404 status code
        client.when()
                .validateHttpResponse(response -> {
                    assertEquals(404, response.statusCode());
                })
                .ping().send();
    }

}
