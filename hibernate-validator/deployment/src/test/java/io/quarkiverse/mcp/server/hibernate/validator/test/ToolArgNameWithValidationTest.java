package io.quarkiverse.mcp.server.hibernate.validator.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ToolInfo;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ToolArgNameWithValidationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testToolArgNameWithValidation() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsList(page -> {
                    ToolInfo findProduct = page.findByName("findProduct");
                    JsonObject schema = findProduct.inputSchema();
                    JsonObject properties = schema.getJsonObject("properties");

                    // Property should be keyed by the @ToolArg name, not the Java param name
                    assertNotNull(properties.getJsonObject("sku_code"), properties.toString());
                    assertNull(properties.getJsonObject("skuCode"), properties.toString());

                    // @Size(min = 3) should be reflected on the correctly named property
                    assertEquals(3, properties.getJsonObject("sku_code").getInteger("minLength"));

                    // "required" should only contain the @ToolArg name
                    JsonArray required = schema.getJsonArray("required");
                    assertTrue(required.contains("sku_code"), required.toString());
                    assertFalse(required.contains("skuCode"), required.toString());

                    ToolInfo getPrice = page.findByName("getPrice");
                    JsonObject priceSchema = getPrice.inputSchema();
                    JsonObject priceProperties = priceSchema.getJsonObject("properties");

                    assertNotNull(priceProperties.getJsonObject("product_price"), priceProperties.toString());
                    assertNull(priceProperties.getJsonObject("price"), priceProperties.toString());
                    assertEquals(1, priceProperties.getJsonObject("product_price").getInteger("minimum"));
                })
                .toolsCall("findProduct", Map.of("sku_code", "ABC-123"), toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("found:ABC-123", toolResponse.firstContent().asText().text());
                })
                .toolsCall("findProduct", Map.of("sku_code", "AB"), toolResponse -> {
                    // @Size(min = 3) violation
                    assertTrue(toolResponse.isError());
                })
                .toolsCall("getPrice", Map.of("product_price", 10), toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("price:10", toolResponse.firstContent().asText().text());
                })
                .toolsCall("getPrice", Map.of("product_price", 0), toolResponse -> {
                    // @Min(1) violation
                    assertTrue(toolResponse.isError());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool(description = "Look up a product by its stock-keeping unit")
        String findProduct(
                @ToolArg(description = "Stock-keeping unit", name = "sku_code") @Size(min = 3) String skuCode) {
            return "found:" + skuCode;
        }

        @Tool(description = "Get product price")
        String getPrice(
                @ToolArg(name = "product_price") @Min(1) Integer price) {
            return "price:" + price;
        }
    }
}
