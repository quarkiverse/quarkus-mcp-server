package io.quarkiverse.mcp.server.schema.validator.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.OutputSchema;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.schema.validator.ToolOutputSchemaValidator;
import io.quarkiverse.mcp.server.schema.validator.test.McpServerTest;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ToolsOutputSchemaValidationOffTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, Price.class))
            .overrideConfigKey("quarkus.mcp.server.schema-validator.tool-output", "off")
            .setLogRecordPredicate(r -> r.getLevel().equals(Level.WARNING)
                    && ToolOutputSchemaValidator.class.getName().equals(r.getLoggerName()))
            .assertLogRecords(records -> assertEquals(0, records.size()));

    @Test
    public void testInvalidOutputIsNotValidated() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("wrongType", toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("twelve", ((JsonObject) toolResponse.structuredContent()).getString("amount"));
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

        @Tool(outputSchema = @OutputSchema(from = Price.class))
        ToolResponse wrongType() {
            return ToolResponse.structuredSuccess(Map.of("amount", "twelve", "currency", "EUR"));
        }

    }

}
