package io.quarkiverse.mcp.server.test.streamablehttp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class UnsupportedSseTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testFailure() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        RestAssured.given()
                .when()
                .get(client.mcpEndpoint())
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
