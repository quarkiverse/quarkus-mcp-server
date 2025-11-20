package io.quarkiverse.mcp.server.test.tools.schemageneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.victools.jsonschema.generator.CustomDefinition;
import com.github.victools.jsonschema.generator.CustomDefinitionProviderV2;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.runtime.SchemaGeneratorConfigCustomizer;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ToolInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ToolsSchemaCustomizerCustomTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(
                            MyToolWithJacksonAnnotatedType.class,
                            BigDecimalAsStringCustomizer.class));

    @Test
    public void testSchemaGenerationWithProvidedCustomizer() {
        McpSseTestClient client = McpAssured.newSseClient()
                .build()
                .connect();
        client.when().toolsList(page -> {
            assertEquals(1, page.tools().size());
            ToolInfo addProducts = page.findByName("add-products");
            JsonObject schema = addProducts.inputSchema();
            assertHasPropertyWithNameType(schema, "products", "array");
            assertHasPropertyCount(schema, 1);
            assertHasRequiredProperties(schema, Set.of("products"));
            JsonObject properties = schema.getJsonObject("properties");
            JsonObject productsProperty = properties.getJsonObject("products");
            JsonObject productType = productsProperty.getJsonObject("items");
            assertNotNull(productType);
            assertEquals("object", productType.getString("type"));
            assertHasPropertyWithNameType(productType, "price", "string");
            assertHasPropertyCount(productType, 1);
        }).thenAssertResults();
    }

    @ApplicationScoped
    public static class BigDecimalAsStringCustomizer implements SchemaGeneratorConfigCustomizer {

        @Override
        public void customize(SchemaGeneratorConfigBuilder builder) {
            CustomDefinitionProviderV2 customDefinitionProvider = (javaType,
                    context) -> javaType.getErasedType() == BigDecimal.class
                            ? new CustomDefinition(context.createDefinition(context.getTypeContext().resolve(String.class)))
                            : null;
            builder.forTypesInGeneral()
                    .withCustomDefinitionProvider(customDefinitionProvider);
        }
    }

    private void assertHasPropertyWithNameType(JsonObject typeObject, String name, String expectedType) {
        JsonObject properties = typeObject.getJsonObject("properties");
        assertNotNull(properties);
        JsonObject property = properties.getJsonObject(name);
        assertNotNull(property);
        assertEquals(expectedType, property.getString("type"));
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
    }

    public static class Product {
        @SuppressWarnings("unused")
        private BigDecimal price;
    }
}
