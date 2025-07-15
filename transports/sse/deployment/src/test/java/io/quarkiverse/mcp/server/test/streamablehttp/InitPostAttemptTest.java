package io.quarkiverse.mcp.server.test.streamablehttp;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;

public class InitPostAttemptTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testFailures() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        URI sseEndpoint = client.sseEndpoint();
        JsonObject initMessage = client.newRequest("initialize");
        JsonObject params = new JsonObject()
                .put("clientInfo", new JsonObject()
                        .put("name", "test")
                        .put("version", "1.0"))
                .put("protocolVersion", "2024-11-05");
        initMessage.put("params", params);
        client.disconnect();
        RestAssured.given()
                .when()
                .body(initMessage.encode())
                .post(sseEndpoint)
                .then()
                .statusCode(405);
    }

    public static class MyTools {

        @Tool
        String bravo(int price) {
            return "" + price * 42;
        }
    }

}
