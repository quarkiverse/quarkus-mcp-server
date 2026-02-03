package io.quarkiverse.mcp.server.test.streamablehttp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;

public class FirstMessageMustBeInitTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testFailures() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        URI mcpEndpoint = client.mcpEndpoint();
        JsonObject toolsCallMessage = client.newRequest(McpMethod.TOOLS_CALL);
        JsonObject params = new JsonObject()
                .put("price", 10);
        toolsCallMessage.put("params", params);
        client.disconnect();
        // We currently do not return the HTTP status 400 but 200
        // and the jsonrpc error message
        String response = RestAssured.given()
                .header("Accept", "text/event-stream, application/json")
                .when()
                .body(toolsCallMessage.encode())
                .post(mcpEndpoint)
                .then()
                .statusCode(200).extract().body().asString();
        assertTrue(response.contains("The first message from the client must be \\\"initialize\\\": tools/call"), response);
    }

    public static class MyTools {

        @Tool
        String bravo(int price) {
            return "" + price * 42;
        }
    }

}
