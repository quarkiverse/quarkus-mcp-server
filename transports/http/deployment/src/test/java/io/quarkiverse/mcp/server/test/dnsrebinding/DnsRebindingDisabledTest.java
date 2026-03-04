package io.quarkiverse.mcp.server.test.dnsrebinding;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;

public class DnsRebindingDisabledTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.http.dns-rebinding-check.enabled", "false")
            .overrideConfigKey("quarkus.mcp.server.http.streamable.dummy-init", "true");

    @Test
    public void testNonLocalhostRequestNotRejected() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        URI endpoint = client.mcpEndpoint();
        client.terminateSession();
        client.disconnect();

        JsonObject response = new JsonObject(RestAssured.given()
                .when()
                .headers(HttpHeaders.HOST + "", "evil.example.com",
                        HttpHeaders.ORIGIN, "http://evil.example.com",
                        HttpHeaders.ACCEPT, "application/json, text/event-stream")
                .body(new JsonObject()
                        .put("jsonrpc", "2.0")
                        .put("method", McpAssured.TOOLS_CALL)
                        .put("id", 1)
                        .put("params", new JsonObject().put("name", "foo")).encode())
                .post(endpoint)
                .then()
                .statusCode(200)
                .extract().body().asString());
        assertNotNull(response.getJsonObject("result").getJsonArray("content").getJsonObject(0).getString("text"));
        assertFalse(response.getJsonObject("result").getBoolean("isError"));
    }

    public static class MyTools {

        @Tool
        String foo(McpConnection connection) {
            return connection.id();
        }

    }

}
