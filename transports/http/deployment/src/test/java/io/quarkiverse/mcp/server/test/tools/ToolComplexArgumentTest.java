package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.runtime.SchemaGeneratorConfigCustomizer;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkiverse.mcp.server.test.tools.ToolComplexArgumentTest.MyTools.MyArg;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ToolComplexArgumentTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, DefinitionsCustomizer.class));

    @Test
    public void testComplexArguments() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .toolsCall("alpha", Map.of("myArg", new MyArg(10, List.of("foo", "bar"))), r -> {
                    assertEquals("MyArg[price=10, names=[foo, bar]]", r.content().get(0).asText().text());
                })
                .toolsCall("alphas", Map.of("myArgs", List.of(new MyArg(10, List.of("foo", "bar")))), r -> {
                    assertEquals("[MyArg[price=10, names=[foo, bar]]]", r.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

    @Test
    public void testComplexArgumentsPreserveDefs() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .toolsList(page -> {
                    // Test processOrder tool with multiple complex types and references
                    // A holder class is generated for non-simple types, so $defs land at the schema root
                    JsonObject schema = page.findByName("processOrder").inputSchema();
                    assertNotNull(schema, "Schema should not be null");

                    JsonObject properties = schema.getJsonObject("properties");
                    assertNotNull(properties);
                    assertNotNull(properties.getJsonObject("customer"));
                    assertNotNull(properties.getJsonObject("billingAddress"));
                    assertNotNull(properties.getJsonObject("shippingAddress"));

                    // $defs should be at the root of the schema
                    JsonObject defs = schema.getJsonObject("$defs");
                    assertNotNull(defs, "$defs should be at the schema root");

                    JsonObject addressDef = defs.getJsonObject("Address");
                    assertNotNull(addressDef, "Address type should be defined in $defs");
                    assertEquals("object", addressDef.getString("type"));

                    JsonObject addressProps = addressDef.getJsonObject("properties");
                    assertNotNull(addressProps);
                    assertNotNull(addressProps.getJsonObject("street"));
                    assertNotNull(addressProps.getJsonObject("city"));
                    assertNotNull(addressProps.getJsonObject("zipCode"));

                    // customer property should reference the Customer definition
                    JsonObject customerProp = properties.getJsonObject("customer");
                    assertNotNull(customerProp);
                    assertEquals("#/$defs/Customer", customerProp.getString("$ref"));

                    // Customer definition should reference Address for defaultAddress
                    JsonObject customerDef = defs.getJsonObject("Customer");
                    assertNotNull(customerDef, "Customer type should be defined in $defs");
                    JsonObject customerProps = customerDef.getJsonObject("properties");
                    assertNotNull(customerProps);
                    JsonObject defaultAddressProp = customerProps.getJsonObject("defaultAddress");
                    assertNotNull(defaultAddressProp);
                    String ref = defaultAddressProp.getString("$ref");
                    assertNotNull(ref, "defaultAddress should use $ref to Address definition");
                    assertEquals("#/$defs/Address", ref,
                            "defaultAddress should reference Address via $ref");
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        String alpha(MyArg myArg) {
            return myArg.toString();
        }

        @Tool
        String alphas(List<MyArg> myArgs) {
            return myArgs.toString();
        }

        @Tool
        String processOrder(Customer customer, Address billingAddress, Address shippingAddress) {
            return "Order processed for " + customer + " billing: " + billingAddress + " shipping: " + shippingAddress;
        }

        public record MyArg(int price, List<String> names) {
        }

        public record Address(String street, String city, String zipCode) {
        }

        public record Customer(String name, String email, Address defaultAddress) {
        }

    }

    @ApplicationScoped
    public static class DefinitionsCustomizer implements SchemaGeneratorConfigCustomizer {

        @Override
        public void customize(SchemaGeneratorConfigBuilder builder) {
            builder.with(Option.DEFINITIONS_FOR_ALL_OBJECTS);
        }
    }

}
