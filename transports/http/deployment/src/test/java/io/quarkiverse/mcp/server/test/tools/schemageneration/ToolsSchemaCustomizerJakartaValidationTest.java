package io.quarkiverse.mcp.server.test.tools.schemageneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ToolInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ToolsSchemaCustomizerJakartaValidationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .overrideRuntimeConfigKey("quarkus.mcp.server.schema-generator.jakarta-validation.enabled", "true")
            .withApplicationRoot(
                    root -> root.addClasses(MyToolWithJakartaValidationAnnotatedType.class));

    @Test
    public void testSchemaGenerationWithJakartaValidationAnnotations() {
        McpSseTestClient client = McpAssured.newSseClient()
                .build()
                .connect();

        client.when().toolsList(page -> {
            assertEquals(2, page.tools().size());
            ToolInfo addProducts = page.findByName("add-products");
            JsonObject schema = addProducts.inputSchema();
            assertHasPropertyWithNameTypeDescription(schema, "products", "array");
            assertHasPropertyCount(schema, 1);
            assertHasRequiredProperties(schema, Set.of("products"));
            JsonObject properties = getProperties(schema);
            JsonObject productsProperty = properties.getJsonObject("products");
            JsonObject productType = productsProperty.getJsonObject("items");
            assertNotNull(productType);
            assertEquals("object", productType.getString("type"));
            assertHasRequiredProperties(productType, Set.of("id", "name", "price"));
            assertHasPropertyWithNameTypeDescription(productType, "id", "string");
            assertPropertyHasMinimumLength(productType, "id", 1);
            assertPropertyHasPattern(productType, "id", "P\\d+");
            assertHasPropertyWithNameTypeDescription(productType, "name", "string");
            assertPropertyHasMinimumLength(productType, "name", 1);
            assertHasPropertyWithNameTypeDescription(productType, "description", "string");
            assertHasPropertyWithNameTypeDescription(productType, "price", "number");
            assertPropertyHasMinimum(productType, "price", 0);
            assertHasPropertyCount(productType, 4);

            ToolInfo noPojo = page.findByName("noPojo");
            JsonObject noPojoSchema = noPojo.inputSchema();
            assertPropertyHasMinimum(noPojoSchema, "age", 1);
        }).thenAssertResults();
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

    @SuppressWarnings("unchecked")
    private void assertHasRequiredProperties(JsonObject typeObject, Set<String> expectedRequireProperties) {
        var requiredProperties = new HashSet<Object>(typeObject.getJsonArray("required").getList());
        assertEquals(expectedRequireProperties, requiredProperties);
    }

    private static JsonObject getProperties(JsonObject typeObject) {
        return typeObject.getJsonObject("properties");
    }

    public static class MyToolWithJakartaValidationAnnotatedType {

        @Tool(name = "add-products", description = "Add multiple products to the product catalog.")
        public String addProducts(
                @ToolArg(name = "products", description = "The products to add to the catalog") List<Product> products) {
            return "ok";
        }

        @Tool
        String noPojo(@Min(1) Integer age) {
            return "ko";
        }
    }

    public static class Product {

        @NotBlank
        @Pattern(regexp = "P\\d+")
        private String id;

        @NotEmpty
        private String name;

        @SuppressWarnings("unused")
        private String description;

        @NotNull
        @Min(0)
        private BigDecimal price;
    }
}
