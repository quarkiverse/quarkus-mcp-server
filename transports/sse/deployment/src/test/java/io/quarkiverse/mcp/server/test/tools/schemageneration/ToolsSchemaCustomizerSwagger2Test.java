package io.quarkiverse.mcp.server.test.tools.schemageneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.swagger.v3.oas.annotations.media.Schema;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ToolsSchemaCustomizerSwagger2Test extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .overrideRuntimeConfigKey("quarkus.mcp.server.schema-generator.swagger2.enabled", "true")
            .withApplicationRoot(
                    root -> root.addClasses(MyToolWithSwagger2AnnotatedType.class));

    @Test
    public void testSchemaGenerationWithSwagger2Annotations() {
        initClient();
        JsonObject toolListMessage = newMessage("tools/list");
        send(toolListMessage);

        JsonObject toolListResponse = waitForLastResponse();

        JsonObject toolListResult = assertResultResponse(toolListMessage, toolListResponse);
        assertNotNull(toolListResult);
        JsonArray tools = toolListResult.getJsonArray("tools");
        assertEquals(1, tools.size());

        assertTool(tools, "add-products", null, schema -> {
            assertHasPropertyWithNameTypeDescription(schema, "products", "array");
            assertHasPropertyCount(schema, 1);
            assertHasRequiredProperties(schema, Set.of("products"));
            JsonObject properties = getProperties(schema);
            JsonObject productsProperty = properties.getJsonObject("products");
            JsonObject productType = productsProperty.getJsonObject("items");
            assertNotNull(productType);
            assertEquals("object", productType.getString("type"));
            assertHasRequiredProperties(productType, Set.of("identifier", "name", "price"));
            assertHasPropertyWithNameTypeDescription(productType, "identifier", "string");
            assertPropertyHasMinimumLength(productType, "identifier", 1);
            assertPropertyHasPattern(productType, "identifier", "P\\d+");
            assertHasPropertyWithNameTypeDescription(productType, "name", "string");
            assertPropertyHasMinimumLength(productType, "name", 1);
            assertHasPropertyWithNameTypeDescription(productType, "description", "string");
            assertHasPropertyWithNameTypeDescription(productType, "price", "number");
            assertPropertyHasMinimum(productType, "price", 0);
            assertHasPropertyCount(productType, 4);
        });
    }

    private void assertHasPropertyWithNameTypeDescription(JsonObject typeObject, String name, String expectedType) {
        JsonObject properties = getProperties(typeObject);
        assertNotNull(properties);
        JsonObject property = properties.getJsonObject(name);
        assertNotNull(property);
        assertEquals(expectedType, property.getString("type"));
    }

    private void assertPropertyHasMinimumLength(JsonObject typeObject, String name, int expectedMinimumLength) {
        JsonObject properties = getProperties(typeObject);
        JsonObject property = properties.getJsonObject(name);
        assertEquals(expectedMinimumLength, property.getInteger("minLength"));
    }

    private void assertPropertyHasMinimum(JsonObject typeObject, String name, int expectedMinimum) {
        JsonObject properties = getProperties(typeObject);
        JsonObject property = properties.getJsonObject(name);
        assertEquals(expectedMinimum, property.getInteger("minimum"));
    }

    private void assertPropertyHasPattern(JsonObject typeObject, String name, String expectedPattern) {
        JsonObject properties = getProperties(typeObject);
        JsonObject property = properties.getJsonObject(name);
        assertEquals(expectedPattern, property.getString("pattern"));
    }

    private void assertHasPropertyCount(JsonObject typeObject, int expectedNumberOfProperties) {
        JsonObject properties = getProperties(typeObject);
        assertNotNull(properties);
        assertEquals(expectedNumberOfProperties, properties.size());
    }

    private void assertHasRequiredProperties(JsonObject typeObject, Set<String> expectedRequireProperties) {
        var requiredProperties = new HashSet<Object>(typeObject.getJsonArray("required").getList());
        assertEquals(expectedRequireProperties, requiredProperties);
    }

    private static JsonObject getProperties(JsonObject typeObject) {
        return typeObject.getJsonObject("properties");
    }

    public static class MyToolWithSwagger2AnnotatedType {

        @Tool(name = "add-products", description = "Add multiple products to the product catalog.")
        public String addProducts(
                @ToolArg(name = "products", description = "The products to add to the catalog") List<Product> products) {
            return "ok";
        }
    }

    public static class Product {

        @Schema(name = "identifier", requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, pattern = "P\\d+")
        private String id;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1)
        private String name;

        private String description;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        private BigDecimal price;
    }
}
