package io.quarkiverse.mcp.server.test.streamablehttp;

import static io.quarkiverse.mcp.server.sse.runtime.StreamableHttpMcpMessageHandler.MCP_SESSION_ID_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import java.util.Map;
import java.util.stream.StreamSupport;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.FooService;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkiverse.mcp.server.test.Options;
import io.quarkiverse.mcp.server.test.tools.MyTools;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;

public class InitFailedStreamableTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(FooService.class, Options.class, Checks.class, MyTools.class));

    @Inject
    ConnectionManager connectionManager;

    @Test
    public void testFailures() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        URI mcpEndpoint = client.mcpEndpoint();
        JsonObject initMessage = client.newMessage("initialize");
        JsonObject params = new JsonObject()
                .put("clientInfo", new JsonObject()
                        .put("name", "test")
                        .put("version", "1.0"))
                .put("protocolVersion", "2024-11-05");
        initMessage.put("params", params);

        // Does not terminate the connection
        client.disconnect();

        // Invalid Accept header
        RestAssured.given()
                .when()
                .body(initMessage.encode())
                .post(mcpEndpoint)
                .then()
                .statusCode(400);
        assertEquals(1, StreamSupport.stream(connectionManager.spliterator(), false).count());

        // Invalid method
        assertNull(RestAssured.given()
                .when()
                .headers(Map.of(HttpHeaders.ACCEPT + "", "application/json, text/event-stream"))
                .body(initMessage.copy().put("method", "bar").encode())
                .post(mcpEndpoint)
                .then()
                .statusCode(200).extract().header(MCP_SESSION_ID_HEADER));
        assertEquals(1, StreamSupport.stream(connectionManager.spliterator(), false).count());

        // Invalid params
        assertNull(RestAssured.given()
                .when()
                .headers(Map.of(HttpHeaders.ACCEPT + "", "application/json, text/event-stream"))
                .body(initMessage.copy().put("params", null).encode())
                .post(mcpEndpoint)
                .then()
                .statusCode(200).extract().header(MCP_SESSION_ID_HEADER));
        assertEquals(1, StreamSupport.stream(connectionManager.spliterator(), false).count());

        // Invalid jsonrpc
        assertNull(RestAssured.given()
                .when()
                .headers(Map.of(HttpHeaders.ACCEPT + "", "application/json, text/event-stream"))
                .body(new JsonObject().put("name", "foo").encode())
                .post(mcpEndpoint)
                .then()
                .statusCode(200).extract().header(MCP_SESSION_ID_HEADER));
        assertEquals(1, StreamSupport.stream(connectionManager.spliterator(), false).count());
    }

}
