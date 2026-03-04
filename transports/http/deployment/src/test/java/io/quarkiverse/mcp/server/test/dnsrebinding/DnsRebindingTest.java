package io.quarkiverse.mcp.server.test.dnsrebinding;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;

public class DnsRebindingTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication();

    @Test
    public void testNonLocalhostRequestRejected() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        URI endpoint = client.mcpEndpoint();
        RestAssured.given()
                .when()
                .headers("Host", "evil.example.com", "Origin", "http://evil.example.com")
                .body(new JsonObject()
                        .put("jsonrpc", "2.0")
                        .put("method", McpAssured.TOOLS_CALL)
                        .put("id", 1)
                        .put("params", new JsonObject().put("name", "foo")).encode())
                .post(endpoint)
                .then()
                .statusCode(403);
    }

}
