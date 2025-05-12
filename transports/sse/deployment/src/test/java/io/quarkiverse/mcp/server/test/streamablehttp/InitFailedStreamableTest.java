package io.quarkiverse.mcp.server.test.streamablehttp;

import static io.quarkiverse.mcp.server.sse.runtime.StreamableHttpMcpMessageHandler.MCP_SESSION_ID_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.stream.StreamSupport;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.FooService;
import io.quarkiverse.mcp.server.test.Options;
import io.quarkiverse.mcp.server.test.tools.MyTools;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;

public class InitFailedStreamableTest extends StreamableHttpTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(FooService.class, Options.class, Checks.class, MyTools.class));

    @Inject
    ConnectionManager connectionManager;

    @Test
    public void testFailures() {
        // Invalid Accept header
        RestAssured.given()
                .when()
                .body(newInitMessage().encode())
                .post(messageEndpoint)
                .then()
                .statusCode(400);
        assertEquals(0, StreamSupport.stream(connectionManager.spliterator(), false).count());

        // Invalid method
        assertNull(RestAssured.given()
                .when()
                .headers(Map.of(HttpHeaders.ACCEPT + "", "application/json, text/event-stream"))
                .body(newInitMessage().put("method", "bar").encode())
                .post(messageEndpoint)
                .then()
                .statusCode(200).extract().header(MCP_SESSION_ID_HEADER));
        assertEquals(0, StreamSupport.stream(connectionManager.spliterator(), false).count());

        // Invalid params
        assertNull(RestAssured.given()
                .when()
                .headers(Map.of(HttpHeaders.ACCEPT + "", "application/json, text/event-stream"))
                .body(newInitMessage().put("params", null).encode())
                .post(messageEndpoint)
                .then()
                .statusCode(200).extract().header(MCP_SESSION_ID_HEADER));
        assertEquals(0, StreamSupport.stream(connectionManager.spliterator(), false).count());

        // Invalid jsonrpc
        assertNull(RestAssured.given()
                .when()
                .headers(Map.of(HttpHeaders.ACCEPT + "", "application/json, text/event-stream"))
                .body(new JsonObject().put("name", "foo").encode())
                .post(messageEndpoint)
                .then()
                .statusCode(200).extract().header(MCP_SESSION_ID_HEADER));
        assertEquals(0, StreamSupport.stream(connectionManager.spliterator(), false).count());
    }

}
