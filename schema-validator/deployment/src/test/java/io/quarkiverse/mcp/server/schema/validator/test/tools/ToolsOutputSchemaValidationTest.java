package io.quarkiverse.mcp.server.schema.validator.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.OutputSchema;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.schema.validator.test.McpServerTest;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.runtime.Startup;
import io.quarkus.test.QuarkusExtensionTest;
import io.vertx.core.json.JsonObject;

public class ToolsOutputSchemaValidationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, Price.class));

    @Test
    public void testToolOutputValidation() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("valid", toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals(12, ((JsonObject) toolResponse.structuredContent()).getInteger("amount"));
                })
                .toolsCall("wrongType", toolResponse -> {
                    assertTrue(toolResponse.isError());
                    assertNull(toolResponse.structuredContent());
                    String text = toolResponse.firstContent().asText().text();
                    assertTrue(text.startsWith("Output validation error: Invalid structured content for tool wrongType"),
                            text);
                    assertTrue(text.contains("/amount"), text);
                })
                .toolsCall("missingStructuredContent", toolResponse -> {
                    assertTrue(toolResponse.isError());
                    assertEquals(
                            "Output validation error: Tool missingStructuredContent has an output schema but no structured content was provided",
                            toolResponse.firstContent().asText().text());
                })
                .toolsCall("toolError", toolResponse -> {
                    // A tool error is passed through unchanged
                    assertTrue(toolResponse.isError());
                    assertEquals("out of stock", toolResponse.firstContent().asText().text());
                })
                .toolsCall("noOutputSchema", toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("ok", toolResponse.firstContent().asText().text());
                })
                .toolsCall("programmatic", toolResponse -> {
                    assertTrue(toolResponse.isError());
                    assertTrue(toolResponse.firstContent().asText().text()
                            .startsWith("Output validation error: Invalid structured content for tool programmatic"));
                })
                .toolsCall("list", Map.of(), toolResponse -> {
                    assertFalse(toolResponse.isError());
                })
                .thenAssertResults();
    }

    // A JavaBean, like the other structured content tests, so the generated schema lists its properties
    public static class Price {

        private int amount;
        private String currency;

        public Price() {
        }

        public Price(int amount, String currency) {
            this.amount = amount;
            this.currency = currency;
        }

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

    }

    public static class MyTools {

        @Inject
        ToolManager toolManager;

        @Tool(structuredContent = true)
        Price valid() {
            return new Price(12, "EUR");
        }

        @Tool(outputSchema = @OutputSchema(from = Price.class))
        ToolResponse wrongType() {
            return ToolResponse.structuredSuccess(Map.of("amount", "twelve", "currency", "EUR"));
        }

        @Tool(outputSchema = @OutputSchema(from = Price.class))
        ToolResponse missingStructuredContent() {
            return ToolResponse.success("12 EUR");
        }

        @Tool(outputSchema = @OutputSchema(from = Price.class))
        ToolResponse toolError() {
            return ToolResponse.error("out of stock");
        }

        @Tool
        String noOutputSchema() {
            return "ok";
        }

        @Tool(structuredContent = true)
        List<Price> list() {
            return List.of(new Price(1, "EUR"), new Price(2, "USD"));
        }

        @Startup
        void onStart() {
            toolManager.newTool("programmatic")
                    .setDescription("Returns a value that does not match its output schema")
                    .generateOutputSchema(Price.class)
                    .setHandler(toolArgs -> ToolResponse.structuredSuccess(Map.of("amount", true)))
                    .register();
        }

    }

}
