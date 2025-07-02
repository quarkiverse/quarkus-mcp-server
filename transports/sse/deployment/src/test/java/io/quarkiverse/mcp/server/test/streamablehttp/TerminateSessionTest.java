package io.quarkiverse.mcp.server.test.streamablehttp;

import static io.quarkiverse.mcp.server.sse.runtime.StreamableHttpMcpMessageHandler.MCP_SESSION_ID_HEADER;

import java.util.Map;

import jakarta.inject.Inject;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class TerminateSessionTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Inject
    ConnectionManager connectionManager;

    @Test
    public void testTerminateSession() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        RestAssured.given()
                .when()
                .headers(Map.of(MCP_SESSION_ID_HEADER, client.mcpSessionId()))
                .delete(client.mcpEndpoint())
                .then();

        // Wait until the connection is removed
        Awaitility.await().until(() -> !connectionManager.has(client.mcpSessionId()));

        // FIXME Send a ping but expect the 400 status code
    }

    public static class MyTools {

        @Tool
        String bravo(int price) {
            return "" + price * 42;
        }
    }

}
