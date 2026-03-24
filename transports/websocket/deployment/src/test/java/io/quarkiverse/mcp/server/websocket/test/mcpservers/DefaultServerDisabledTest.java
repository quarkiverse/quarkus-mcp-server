package io.quarkiverse.mcp.server.websocket.test.mcpservers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpWebSocketTestClient;
import io.quarkiverse.mcp.server.websocket.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.http.HttpHeaders;

public class DefaultServerDisabledTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = config(500)
            .withApplicationRoot(
                    root -> root.addClasses(MyFeatures.class))
            .overrideConfigKey("quarkus.mcp.server.websocket.enabled", "false")
            .overrideConfigKey("quarkus.mcp.server.bravo.websocket.endpoint-path", "/bravo/mcp");

    static QuarkusUnitTest config(int textLimit) {
        QuarkusUnitTest ret = defaultConfig(500);
        if (System.getProperty("logTraffic") != null) {
            ret.overrideConfigKey("quarkus.mcp.server.bravo.traffic-logging.enabled", "true");
            ret.overrideConfigKey("quarkus.mcp.server.bravo.traffic-logging.text-limit", "" + textLimit);
        }
        return ret;
    }

    @Test
    public void testServers() {
        // HTTP transport is disabled for the default server
        RestAssured.given()
                .when()
                .headers(HttpHeaders.ACCEPT + "", "application/json, text/event-stream")
                .body("TEST")
                .post(testUri.toString() + "/mcp")
                .then()
                .statusCode(404);

        McpWebSocketTestClient client = McpAssured.newWebSocketClient()
                .setEndpointPath("/bravo/mcp")
                .build()
                .connect();

        client.when()
                .toolsCall("bravo", response -> {
                    assertEquals("2", response.firstContent().asText().text());
                })
                .toolsCall("alpha")
                .withErrorAssert(error -> {
                    assertEquals("Invalid tool name: alpha", error.message());
                })
                .send()
                .toolsCall("charlie")
                .withErrorAssert(error -> {
                    assertEquals("Invalid tool name: charlie", error.message());
                })
                .send()
                .thenAssertResults();
    }

    public static class MyFeatures {

        @McpServer("bravo")
        @Tool
        String bravo() {
            return "2";
        }

    }
}
