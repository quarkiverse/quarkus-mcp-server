package io.quarkiverse.mcp.server.test.dummyinit;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.http.runtime.StreamableHttpMcpMessageHandler;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;

/**
 * Verify that the old config key {@code quarkus.mcp.server.http.streamable.dummy-init}
 * still works as a fallback for the new {@code auto-init} key.
 */
public class AutoInitConfigFallbackTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class))
            // Use the old config key
            .overrideConfigKey("quarkus.mcp.server.http.streamable.dummy-init", "true");

    @Inject
    ConnectionManager connectionManager;

    @Test
    public void testOldConfigKeyStillWorks() {
        int id = 0;
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        URI endpoint = client.mcpEndpoint();
        client.terminateSession();
        client.disconnect();

        // Send a tool call without a valid session - auto-init should kick in via the old config key
        JsonObject response = new JsonObject(RestAssured.given()
                .when()
                .headers(StreamableHttpMcpMessageHandler.MCP_SESSION_ID_HEADER, "nonExistentSession",
                        HttpHeaders.ACCEPT + "", "application/json, text/event-stream")
                .body(toolsCall("echo", id++).encode())
                .post(endpoint)
                .then()
                .statusCode(200)
                .extract().body().asString());
        String connectionId = response.getJsonObject("result").getJsonArray("content").getJsonObject(0).getString("text");
        // The auto-initialized connection should have been removed
        assertFalse(connectionManager.has(connectionId));
        assertFalse(response.getJsonObject("result").getBoolean("isError"));
    }

    private JsonObject toolsCall(String name, int id) {
        return new JsonObject()
                .put("jsonrpc", "2.0")
                .put("method", McpAssured.TOOLS_CALL)
                .put("id", id)
                .put("params", new JsonObject().put("name", name));
    }

    public static class MyTools {

        @Tool
        String echo(McpConnection connection) {
            if (!connection.initialRequest().implementation().name()
                    .equals(StreamableHttpMcpMessageHandler.AUTO_INIT_IMPL_NAME)) {
                throw new IllegalStateException("Expected auto-init implementation name");
            }
            return connection.id();
        }
    }

}
