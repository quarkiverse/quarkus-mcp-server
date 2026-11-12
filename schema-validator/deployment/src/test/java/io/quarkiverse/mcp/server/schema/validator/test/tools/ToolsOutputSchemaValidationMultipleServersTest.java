package io.quarkiverse.mcp.server.schema.validator.test.tools;

import static io.quarkiverse.mcp.server.McpServer.DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.OutputSchema;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.schema.validator.test.McpServerTest;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.QuarkusExtensionTest;
import io.vertx.core.json.JsonObject;

/**
 * Tools with the same name on different servers are validated against their own output schemas.
 */
public class ToolsOutputSchemaValidationMultipleServersTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(AlphaTools.class, BravoTools.class, AlphaPrice.class, BravoPrice.class))
            .overrideConfigKey("quarkus.mcp.server.support-multi-server-bindings", "false")
            .overrideConfigKey("quarkus.mcp.server.http.root-path", "/alpha/mcp")
            .overrideConfigKey("quarkus.mcp.server.bravo.http.root-path", "/bravo/mcp");

    @Test
    public void testEachServerUsesItsOwnSchema() {
        McpStreamableTestClient alpha = McpAssured.newStreamableClient().setMcpPath("/alpha/mcp").build().connect();
        McpStreamableTestClient bravo = McpAssured.newStreamableClient().setMcpPath("/bravo/mcp").build().connect();
        // Alternate between the servers, so that each call finds the other server's tool in a cache keyed by name only
        for (int i = 0; i < 2; i++) {
            alpha.when()
                    .toolsCall("price", r -> {
                        assertFalse(r.isError());
                        assertEquals(12, ((JsonObject) r.structuredContent()).getInteger("amount"));
                    })
                    .toolsCall("wrong", r -> {
                        assertTrue(r.isError());
                        assertTrue(r.firstContent().asText().text().contains("/amount"), r.firstContent().asText().text());
                    })
                    .thenAssertResults();
            bravo.when()
                    .toolsCall("price", r -> {
                        assertFalse(r.isError());
                        assertEquals("twelve", ((JsonObject) r.structuredContent()).getString("amount"));
                    })
                    .toolsCall("wrong", r -> {
                        assertTrue(r.isError());
                        assertTrue(r.firstContent().asText().text().contains("/amount"), r.firstContent().asText().text());
                    })
                    .thenAssertResults();
        }
    }

    public static class AlphaPrice {

        private int amount;

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }

    }

    public static class BravoPrice {

        private String amount;

        public String getAmount() {
            return amount;
        }

        public void setAmount(String amount) {
            this.amount = amount;
        }

    }

    @McpServer(DEFAULT)
    public static class AlphaTools {

        @Tool(outputSchema = @OutputSchema(from = AlphaPrice.class))
        ToolResponse price() {
            return ToolResponse.structuredSuccess(Map.of("amount", 12));
        }

        @Tool(outputSchema = @OutputSchema(from = AlphaPrice.class))
        ToolResponse wrong() {
            return ToolResponse.structuredSuccess(Map.of("amount", "twelve"));
        }

    }

    @McpServer("bravo")
    public static class BravoTools {

        @Tool(outputSchema = @OutputSchema(from = BravoPrice.class))
        ToolResponse price() {
            return ToolResponse.structuredSuccess(Map.of("amount", "twelve"));
        }

        @Tool(outputSchema = @OutputSchema(from = BravoPrice.class))
        ToolResponse wrong() {
            return ToolResponse.structuredSuccess(Map.of("amount", 12));
        }

    }

}
