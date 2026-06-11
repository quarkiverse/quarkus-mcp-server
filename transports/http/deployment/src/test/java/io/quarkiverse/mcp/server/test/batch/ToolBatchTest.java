package io.quarkiverse.mcp.server.test.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ToolBatchTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testBatchMessageRejected() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        JsonArray batch = new JsonArray()
                .add(client.newRequest("tools/call")
                        .put("params",
                                new JsonObject().put("name", "bravo").put("arguments", new JsonObject().put("price", 10))))
                .add(client.newRequest("tools/call")
                        .put("params",
                                new JsonObject().put("name", "bravo").put("arguments", new JsonObject().put("price", 100))));

        String responseBody = RestAssured.given()
                .when()
                .headers(Map.of(
                        HttpHeaders.ACCEPT + "", "application/json, text/event-stream",
                        "Mcp-Session-Id", client.mcpSessionId()))
                .body(batch.encode())
                .post(client.mcpEndpoint().toString())
                .then()
                .statusCode(200)
                .extract().body().asString();

        JsonObject body = new JsonObject(responseBody);
        assertNotNull(body.getJsonObject("error"));
        assertEquals(JsonRpcErrorCodes.PARSE_ERROR, body.getJsonObject("error").getInteger("code"));
    }

    public static class MyTools {

        @Tool
        String bravo(int price) {
            return "" + price * 42;
        }

    }

}
