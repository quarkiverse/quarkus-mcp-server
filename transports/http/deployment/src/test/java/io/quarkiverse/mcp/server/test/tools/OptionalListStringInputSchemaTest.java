package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class OptionalListStringInputSchemaTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(1000)
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testInputSchema() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .toolsList(page -> {
                    assertEquals(5, page.size());
                    assertEquals(
                            "{\"type\":\"object\",\"properties\":{\"tasks\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},\"required\":[]}",
                            page.findByName("runOptionalTasks").inputSchema().toString());
                    assertEquals(
                            "{\"type\":\"object\",\"properties\":{\"tasks\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},\"required\":[\"tasks\"]}",
                            page.findByName("runTasks").inputSchema().toString());

                    JsonObject optIntSchema = page.findByName("withOptionalInt").inputSchema();
                    JsonObject optIntProps = optIntSchema.getJsonObject("properties");
                    assertEquals("integer", optIntProps.getJsonObject("value").getString("type"));
                    assertTrue(optIntSchema.getJsonArray("required").isEmpty());

                    JsonObject optLongSchema = page.findByName("withOptionalLong").inputSchema();
                    JsonObject optLongProps = optLongSchema.getJsonObject("properties");
                    assertEquals("integer", optLongProps.getJsonObject("value").getString("type"));
                    assertTrue(optLongSchema.getJsonArray("required").isEmpty());

                    JsonObject optDoubleSchema = page.findByName("withOptionalDouble").inputSchema();
                    JsonObject optDoubleProps = optDoubleSchema.getJsonObject("properties");
                    assertEquals("number", optDoubleProps.getJsonObject("value").getString("type"));
                    assertTrue(optDoubleSchema.getJsonArray("required").isEmpty());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        public String runTasks(List<String> tasks) {
            return "ok";
        }

        @Tool
        public String runOptionalTasks(Optional<List<String>> tasks) {
            return "ok";
        }

        @Tool
        public String withOptionalInt(OptionalInt value) {
            return "ok";
        }

        @Tool
        public String withOptionalLong(OptionalLong value) {
            return "ok";
        }

        @Tool
        public String withOptionalDouble(OptionalDouble value) {
            return "ok";
        }

    }

}
