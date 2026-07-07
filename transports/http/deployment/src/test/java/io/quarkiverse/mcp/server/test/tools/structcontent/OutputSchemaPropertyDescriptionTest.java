package io.quarkiverse.mcp.server.test.tools.structcontent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class OutputSchemaPropertyDescriptionTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(5000)
            .overrideRuntimeConfigKey("quarkus.mcp.server.schema-generator.jackson.enabled", "true")
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, Foo.class, Bar.class));

    @Test
    public void testOutputSchemaPropertyDescription() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsList(page -> {
                    assertEquals(2, page.tools().size());

                    // Verify output schema for tool returning a class with @JsonPropertyDescription on a field
                    JsonObject fooSchema = page.findByName("fooTool").outputSchema();
                    assertNotNull(fooSchema, "Output schema for fooTool should not be null");
                    JsonObject fooProperties = fooSchema.getJsonObject("properties");
                    assertNotNull(fooProperties);
                    JsonObject barProp = fooProperties.getJsonObject("bar");
                    assertNotNull(barProp);
                    assertEquals("string", barProp.getString("type"));
                    assertEquals("bar description", barProp.getString("description"));

                    // Verify output schema for tool returning a record with @JsonPropertyDescription
                    JsonObject barSchema = page.findByName("barTool").outputSchema();
                    assertNotNull(barSchema, "Output schema for barTool should not be null");
                    JsonObject barProperties = barSchema.getJsonObject("properties");
                    assertNotNull(barProperties);
                    JsonObject valueProp = barProperties.getJsonObject("value");
                    assertNotNull(valueProp);
                    assertEquals("string", valueProp.getString("type"));
                    assertEquals("value description", valueProp.getString("description"));
                })
                .toolsCall("fooTool", toolResponse -> {
                    assertEquals(0, toolResponse.content().size());
                    assertNotNull(toolResponse.structuredContent());
                    if (toolResponse.structuredContent() instanceof JsonObject json) {
                        assertEquals("hello", json.getString("bar"));
                    } else {
                        fail("Not a JsonObject");
                    }
                })
                .toolsCall("barTool", toolResponse -> {
                    assertEquals(0, toolResponse.content().size());
                    assertNotNull(toolResponse.structuredContent());
                    if (toolResponse.structuredContent() instanceof JsonObject json) {
                        assertEquals("world", json.getString("value"));
                    } else {
                        fail("Not a JsonObject");
                    }
                })
                .thenAssertResults();
    }

    public static class Foo {

        @JsonPropertyDescription("bar description")
        public String bar;
    }

    public record Bar(
            @JsonPropertyDescription("value description") String value) {
    }

    public static class MyTools {

        @Tool(description = "Returns a Foo", structuredContent = true)
        Foo fooTool() {
            Foo foo = new Foo();
            foo.bar = "hello";
            return foo;
        }

        @Tool(description = "Returns a Bar", structuredContent = true)
        Bar barTool() {
            return new Bar("world");
        }
    }
}
