package io.quarkiverse.mcp.server.schema.validator.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.schema.validator.test.McpServerTest;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ToolsSchemaValidationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testTools() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("bravo", Map.of("price", 12), toolResponse -> {
                    assertEquals("foo12", toolResponse.content().get(0).asText().text());
                })
                .toolsCall("bravo", Map.of("price", 21), toolResponse -> {
                    assertEquals("foo21", toolResponse.content().get(0).asText().text());
                })
                // Send a tools/list request without the required "params" field
                .message(client.newRequest("tools/list").put("params", new JsonObject().put("cursor", true)))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertTrue(error.message().startsWith("Schema validation failed"));
                })
                .send()
                // Send a tools/call request without the required "params" field
                .message(client.newRequest("tools/call"))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertTrue(error.message().startsWith("Schema validation failed"));
                })
                .send()
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        String bravo(int price) {
            return "foo" + price;
        }

    }

}
