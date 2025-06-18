package io.quarkiverse.mcp.server.test.tools.schemageneration;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ToolsSchemaCustomizerJacksonTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .overrideRuntimeConfigKey("quarkus.mcp.server.schema-generator.jackson.enabled", "true")
            .withApplicationRoot(
                    root -> root.addClasses(MyToolWithJacksonAnnotatedType.class));

    @Test
    public void testSchemaGenerationWithJacksonAnnotation() {
        initClient();
        JsonObject toolListMessage = newMessage("tools/list");
        send(toolListMessage);

        JsonObject toolListResponse = waitForLastResponse();

        JsonObject toolListResult = assertResultResponse(toolListMessage, toolListResponse);
        assertNotNull(toolListResult);
        JsonArray tools = toolListResult.getJsonArray("tools");
        assertEquals(1, tools.size());

        assertTool(tools, "add-products", null, schema -> {
            assertHasPropertyWithNameTypeDescription(schema, "products", "array", "The products to add to the catalog");
            assertHasPropertyCount(schema, 1);
            assertHasRequiredProperties(schema, Set.of("products"));
            JsonObject properties = schema.getJsonObject("properties");
            JsonObject productsProperty = properties.getJsonObject("products");
            JsonObject productType = productsProperty.getJsonObject("items");
            assertNotNull(productType);
            assertEquals("object", productType.getString("type"));
            assertHasRequiredProperties(productType, Set.of("identifier", "name", "price"));
            assertHasPropertyWithNameTypeDescription(productType, "identifier", "string",
                    "The unique identifier of the product.");
            assertHasPropertyWithNameTypeDescription(productType, "name", "string", "The name of the product.");
            assertHasPropertyWithNameTypeDescription(productType, "description", "string", null);
            assertHasPropertyWithNameTypeDescription(productType, "price", "number", null);
            assertHasPropertyCount(productType, 4);
        });
    }

    private void assertHasPropertyWithNameTypeDescription(JsonObject typeObject, String name, String expectedType,
            String expectedDescription) {
        JsonObject properties = typeObject.getJsonObject("properties");
        assertNotNull(properties);
        JsonObject property = properties.getJsonObject(name);
        assertNotNull(property);
        assertEquals(expectedType, property.getString("type"));
        assertEquals(expectedDescription, property.getString("description"));
    }

    private void assertHasPropertyCount(JsonObject typeObject, int expectedNumberOfProperties) {
        JsonObject properties = typeObject.getJsonObject("properties");
        assertNotNull(properties);
        assertEquals(expectedNumberOfProperties, properties.size());
    }

    private void assertHasRequiredProperties(JsonObject typeObject, Set<String> expectedRequireProperties) {
        var requiredProperties = new HashSet<Object>(typeObject.getJsonArray("required").getList());
        assertEquals(expectedRequireProperties, requiredProperties);
    }

    public static class MyToolWithJacksonAnnotatedType {

        @Tool(name = "add-products", description = "Add multiple products to the product catalog.")
        public String addProducts(
                @ToolArg(name = "products", description = "The products to add to the catalog") List<Product> products) {
            return "ok";
        }
    }

    public static class Product {

        @JsonProperty(value = "identifier", required = true)
        @JsonPropertyDescription("The unique identifier of the product.")
        private String id;

        @JsonProperty(value = "name", required = true)
        @JsonPropertyDescription("The name of the product.")
        private String name;

        @JsonProperty(value = "description", required = false)
        private String description;

        @JsonProperty(value = "price", required = true)
        private BigDecimal price;
    }
}
