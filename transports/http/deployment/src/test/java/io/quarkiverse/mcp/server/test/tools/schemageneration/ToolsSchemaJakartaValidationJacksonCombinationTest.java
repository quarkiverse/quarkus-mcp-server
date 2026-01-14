package io.quarkiverse.mcp.server.test.tools.schemageneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ToolInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ToolsSchemaJakartaValidationJacksonCombinationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(2000)
            .overrideRuntimeConfigKey("quarkus.mcp.server.schema-generator.jackson.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.mcp.server.schema-generator.jakarta-validation.enabled", "true")
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testSchemaGenerationWithJakartaValidationAnnotations() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when().toolsList(page -> {
            assertEquals(1, page.tools().size());
            ToolInfo addProduct = page.findByName("addProduct");
            JsonObject addProductSchema = addProduct.inputSchema();
            assertProductType(getProperties(addProductSchema).getJsonObject("product"));
        }).thenAssertResults();
    }

    private static void assertProductType(JsonObject productType) {
        assertNotNull(productType);
        assertEquals("object", productType.getString("type"));
        assertHasRequiredProperties(productType, Set.of("id", "name", "price"));
        assertHasPropertyWithNameTypeDescription(productType, "id", "string", null);
        assertPropertyHasMinimumLength(productType, "id", 1);
        assertPropertyHasPattern(productType, "id", "P\\d+");
        assertHasPropertyWithNameTypeDescription(productType, "name", "string", "The name of the product.");
        assertPropertyHasMinimumLength(productType, "name", 1);
        assertHasPropertyWithNameTypeDescription(productType, "description", "string", null);
        assertHasPropertyWithNameTypeDescription(productType, "price", "number", null);
        assertPropertyHasMinimum(productType, "price", 0);
        assertHasPropertyCount(productType, 4);
    }

    private static void assertHasPropertyWithNameTypeDescription(JsonObject typeObject, String name, String expectedType,
            String expectedDescription) {
        JsonObject properties = getProperties(typeObject);
        assertNotNull(properties);
        JsonObject property = properties.getJsonObject(name);
        assertNotNull(property);
        assertEquals(expectedType, property.getString("type"));
        if (expectedDescription != null) {
            assertEquals(expectedDescription, property.getString("description"));
        }
    }

    private static void assertPropertyHasMinimumLength(JsonObject typeObject, String name, int expectedMinimumLength) {
        JsonObject properties = getProperties(typeObject);
        JsonObject property = properties.getJsonObject(name);
        assertEquals(expectedMinimumLength, property.getInteger("minLength"));
    }

    private static void assertPropertyHasMinimum(JsonObject typeObject, String name, int expectedMinimum) {
        JsonObject properties = getProperties(typeObject);
        JsonObject property = properties.getJsonObject(name);
        assertEquals(expectedMinimum, property.getInteger("minimum"));
    }

    private static void assertPropertyHasPattern(JsonObject typeObject, String name, String expectedPattern) {
        JsonObject properties = getProperties(typeObject);
        JsonObject property = properties.getJsonObject(name);
        assertEquals(expectedPattern, property.getString("pattern"));
    }

    private static void assertHasPropertyCount(JsonObject typeObject, int expectedNumberOfProperties) {
        JsonObject properties = getProperties(typeObject);
        assertNotNull(properties);
        assertEquals(expectedNumberOfProperties, properties.size());
    }

    @SuppressWarnings("unchecked")
    private static void assertHasRequiredProperties(JsonObject typeObject, Set<String> expectedRequireProperties) {
        var requiredProperties = new HashSet<Object>(typeObject.getJsonArray("required").getList());
        assertEquals(expectedRequireProperties, requiredProperties);
    }

    private static JsonObject getProperties(JsonObject typeObject) {
        return typeObject.getJsonObject("properties");
    }

    public static class MyTools {

        @Tool
        String addProduct(Product product, @Min(19) int age) {
            return "yes";
        }
    }

    public static class Product {

        @NotBlank
        @Pattern(regexp = "P\\d+")
        public String id;

        @JsonProperty(value = "name", required = true)
        @JsonPropertyDescription("The name of the product.")
        @NotEmpty
        private String name;

        public String description;

        @NotNull
        @Min(0)
        public BigDecimal price;
    }
}
