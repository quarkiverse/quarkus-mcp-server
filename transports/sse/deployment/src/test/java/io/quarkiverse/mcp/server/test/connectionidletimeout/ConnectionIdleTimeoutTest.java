package io.quarkiverse.mcp.server.test.connectionidletimeout;

import static io.restassured.RestAssured.given;

import java.net.URI;

import jakarta.inject.Inject;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.ContentType;

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
        URI endpoint = initClient();
        String endpointStr = endpoint.toString();
        String id = endpointStr.substring(endpointStr.lastIndexOf("/") + 1);
        Awaitility.await().until(() -> !connectionManager.has(id));
        given()
                .contentType(ContentType.JSON)
                .when()
                .body(newMessage("ping").encode())
                .post(endpoint)
                .then()
                .statusCode(400);
    }
}
