package io.quarkiverse.mcp.server.hibernate.validator.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;

@QuarkusTest
class ConstraintViolationsTest {

    @Test
    void testTool() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsList(p -> {
                    assertEquals(1, p.size());
                    JsonObject schema = p.findByName("bravo").inputSchema();
                    JsonObject properties = schema.getJsonObject("properties");
                    assertEquals(1, properties.size());
                    JsonObject price = properties.getJsonObject("price");
                    assertNotNull(price);
                    assertEquals("integer", price.getString("type"));
                    assertEquals(5, price.getInteger("minimum"));

                })
                .toolsCall("bravo", Map.of("price", 1, "name", ""), toolResponse -> {
                    assertTrue(toolResponse.isError());
                    assertEquals("bravo.price: must be greater than or equal to 5",
                            toolResponse.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

}
