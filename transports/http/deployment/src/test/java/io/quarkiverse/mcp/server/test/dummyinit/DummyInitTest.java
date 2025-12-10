package io.quarkiverse.mcp.server.test.dummyinit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.util.stream.StreamSupport;

import jakarta.inject.Inject;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.WrapBusinessError;
import io.quarkiverse.mcp.server.http.runtime.StreamableHttpMcpMessageHandler;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;

public class DummyInitTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.http.streamable.dummy-init", "true");

    @Inject
    ConnectionManager connectionManager;

    @Test
    public void testToolCall() {
        int id = 0;
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        URI endpoint = client.mcpEndpoint();
        client.terminateSession();
        client.disconnect();

        JsonObject response = new JsonObject(RestAssured.given()
                .when()
                // Non-existent session should not be a problem
                .headers(StreamableHttpMcpMessageHandler.MCP_SESSION_ID_HEADER, "definitelyDoesNotExist",
                        HttpHeaders.ACCEPT + "", "application/json, text/event-stream")
                .body(toolsCall("bravo", id++).encode())
                .post(endpoint)
                .then()
                .statusCode(200)
                .extract().body().asString());
        String connectionId = response.getJsonObject("result").getJsonArray("content").getJsonObject(0).getString("text");
        assertFalse(connectionManager.has(connectionId));
        assertFalse(response.getJsonObject("result").getBoolean("isError"));

        response = new JsonObject(RestAssured.given()
                .when()
                // .log().all()
                .headers(HttpHeaders.ACCEPT + "",
                        "application/json, text/event-stream")
                .body(toolsCall("charlie", id++).encode())
                .post(endpoint)
                .then()
                .statusCode(200)
                .extract().body().asString());
        assertTrue(response.getJsonObject("result").getBoolean("isError"));

        try {
            Awaitility.await().until(() -> !connectionManager.iterator().hasNext());
        } catch (ConditionTimeoutException e) {
            fail("Some connections still exits: "
                    + StreamSupport.stream(connectionManager.spliterator(), false)
                            .map(c -> c.id() + "[" + c.initialRequest().implementation().name() + "]").toList());
        }
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
        String bravo(McpConnection connection) {
            if (!connection.initialRequest().implementation().name()
                    .equals(StreamableHttpMcpMessageHandler.DUMMY_INIT_IMPL_NAME)) {
                throw new IllegalStateException();
            }
            return connection.id();
        }

        @WrapBusinessError
        @Tool
        String charlie() {
            throw new IllegalArgumentException();
        }
    }

}
