package io.quarkiverse.mcp.server.schema.validator.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.schema.validator.test.McpServerTest;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.runtime.Startup;
import io.quarkus.test.QuarkusUnitTest;

public class ToolsInputSchemaValidationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, Item.class));

    @Test
    public void testToolInputValidation() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("discount", Map.of("percent", 5), toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("percent=5", toolResponse.firstContent().asText().text());
                })
                // Without validation, 2.7 would be converted to 2 and the tool would be called
                .toolsCall("discount", Map.of("percent", 2.7), toolResponse -> {
                    assertTrue(toolResponse.isError());
                    String text = toolResponse.firstContent().asText().text();
                    assertTrue(text.startsWith("Input validation error: Invalid arguments for tool discount"), text);
                    assertTrue(text.contains("/percent"), text);
                })
                .toolsCall("discount", Map.of("percent", "42"), toolResponse -> {
                    assertTrue(toolResponse.isError());
                    assertTrue(toolResponse.firstContent().asText().text().contains("/percent"));
                })
                .toolsCall("discount", Map.of(), toolResponse -> {
                    // A missing required argument is reported as a tool error too
                    assertTrue(toolResponse.isError());
                    assertTrue(toolResponse.firstContent().asText().text()
                            .startsWith("Input validation error: Invalid arguments for tool discount"));
                })
                .toolsCall("order", Map.of("item", Map.of("name", "book", "quantity", 2)), toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("book x 2", toolResponse.firstContent().asText().text());
                })
                .toolsCall("order", Map.of("item", Map.of("name", "book", "quantity", "two")), toolResponse -> {
                    assertTrue(toolResponse.isError());
                    assertTrue(toolResponse.firstContent().asText().text().contains("/item/quantity"));
                })
                .toolsCall("programmatic", Map.of("count", true), toolResponse -> {
                    assertTrue(toolResponse.isError());
                    assertTrue(toolResponse.firstContent().asText().text()
                            .startsWith("Input validation error: Invalid arguments for tool programmatic"));
                })
                .toolsCall("noArguments", toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("ok", toolResponse.firstContent().asText().text());
                })
                .thenAssertResults();
        // Only the two valid calls reached the tools
        assertEquals(2, MyTools.CALLS.get());
    }

    public static class Item {

        private String name;
        private int quantity;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

    }

    public static class MyTools {

        static final AtomicInteger CALLS = new AtomicInteger();

        @Inject
        ToolManager toolManager;

        @Tool
        String discount(int percent) {
            CALLS.incrementAndGet();
            return "percent=" + percent;
        }

        @Tool
        String order(Item item) {
            CALLS.incrementAndGet();
            return item.getName() + " x " + item.getQuantity();
        }

        @Tool
        String noArguments() {
            return "ok";
        }

        @Startup
        void onStart() {
            toolManager.newTool("programmatic")
                    .setDescription("Counts")
                    .addArgument("count", "The count", true, int.class)
                    .setHandler(toolArgs -> {
                        CALLS.incrementAndGet();
                        return ToolResponse.success("count=" + toolArgs.args().get("count"));
                    })
                    .register();
        }

    }

}
