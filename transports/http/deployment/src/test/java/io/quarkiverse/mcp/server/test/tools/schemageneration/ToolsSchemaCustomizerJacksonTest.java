package io.quarkiverse.mcp.server.test.tools.schemageneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.quarkiverse.mcp.server.InputSchemaGenerator;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.InputSchema;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ToolInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.jackson.ObjectMapperCustomizer;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ToolsSchemaCustomizerJacksonTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(2000)
            .overrideRuntimeConfigKey("quarkus.mcp.server.schema-generator.jackson.enabled", "true")
            .withApplicationRoot(
                    root -> root.addClasses(MyToolWithJacksonAnnotatedType.class, CustomItem.class,
                            CustomItemCustomizer.class, CustomItemSchemaGenerator.class));

    @Test
    public void testSchemaGenerationWithJacksonAnnotation() {
        McpSseTestClient client = McpAssured.newSseClient()
                .build()
                .connect();
        client.when().toolsList(page -> {
            assertEquals(3, page.tools().size());

            ToolInfo addProducts = page.findByName("add-products");
            JsonObject addProductsSchema = addProducts.inputSchema();
            assertHasPropertyWithNameTypeDescription(addProductsSchema, "products", "array",
                    "The products to add to the catalog");
            assertHasPropertyCount(addProductsSchema, 1);
            assertHasRequiredProperties(addProductsSchema, Set.of("products"));
            JsonObject properties = addProductsSchema.getJsonObject("properties");
            JsonObject productsProperty = properties.getJsonObject("products");
            assertProductType(productsProperty.getJsonObject("items"));

            ToolInfo addProduct = page.findByName("addProduct");
            JsonObject addProductSchema = addProduct.inputSchema();
            assertProductType(addProductSchema.getJsonObject("properties").getJsonObject("product"));

            // CustomItem uses a custom Jackson serializer that writes a plain JSON string.
            // The victools JacksonModule does not introspect programmatic serializers,
            // so a custom InputSchemaGenerator is used as a workaround.
            ToolInfo customItemTool = page.findByName("customItemTool");
            JsonObject customItemSchema = customItemTool.inputSchema();
            assertHasPropertyCount(customItemSchema, 1);
            assertHasRequiredProperties(customItemSchema, Set.of("item"));
            assertHasPropertyWithNameTypeDescription(customItemSchema, "item", "string",
                    "Custom item in the format 'name:value'");

        }).thenAssertResults();
    }

    private void assertProductType(JsonObject productType) {
        assertNotNull(productType);
        assertEquals("object", productType.getString("type"));
        assertHasRequiredProperties(productType, Set.of("identifier", "name", "price"));
        assertHasPropertyWithNameTypeDescription(productType, "identifier", "string",
                "The unique identifier of the product.");
        assertHasPropertyWithNameTypeDescription(productType, "name", "string", "The name of the product.");
        assertHasPropertyWithNameTypeDescription(productType, "description", "string", null);
        assertHasPropertyWithNameTypeDescription(productType, "price", "number", null);
        assertHasPropertyCount(productType, 4);
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

    @SuppressWarnings("unchecked")
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

        @Tool
        String addProduct(Product product) {
            return "yes";
        }

        // The victools JacksonModule only handles Jackson annotations, not custom serializers
        // registered programmatically via SimpleModule. Use a custom InputSchemaGenerator as a workaround.
        @Tool(inputSchema = @InputSchema(generator = CustomItemSchemaGenerator.class))
        String customItemTool(CustomItem item) {
            return item.toString();
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

    public record CustomItem(String name, int value) {
    }

    @Singleton
    public static class CustomItemSchemaGenerator implements InputSchemaGenerator<Object> {

        @Override
        public Object generate(ToolManager.ToolInfo tool) {
            return new JsonObject()
                    .put("type", "object")
                    .put("properties", new JsonObject()
                            .put("item", new JsonObject()
                                    .put("type", "string")
                                    .put("description", "Custom item in the format 'name:value'")))
                    .put("required", List.of("item"));
        }
    }

    @Singleton
    public static class CustomItemCustomizer implements ObjectMapperCustomizer {

        @Override
        public void customize(ObjectMapper mapper) {
            SimpleModule module = new SimpleModule();
            module.addSerializer(CustomItem.class, new JsonSerializer<CustomItem>() {
                @Override
                public void serialize(CustomItem item, JsonGenerator gen, SerializerProvider serializers)
                        throws IOException {
                    gen.writeString(item.name() + ":" + item.value());
                }
            });
            module.addDeserializer(CustomItem.class, new JsonDeserializer<CustomItem>() {
                @Override
                public CustomItem deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                    String[] parts = p.getText().split(":");
                    return new CustomItem(parts[0], Integer.parseInt(parts[1]));
                }
            });
            mapper.registerModule(module);
        }
    }
}
