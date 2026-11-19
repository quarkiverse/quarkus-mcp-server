package io.quarkiverse.mcp.server.schema.validator.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.schema.validator.ToolInputSchemaValidator;
import io.quarkiverse.mcp.server.schema.validator.test.McpServerTest;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.QuarkusUnitTest;

public class ToolsInputSchemaValidationLogTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.schema-validator.tools.input", "log")
            .setLogRecordPredicate(r -> r.getLevel().equals(Level.WARNING)
                    && ToolInputSchemaValidator.class.getName().equals(r.getLoggerName()))
            .assertLogRecords(records -> assertEquals(1, records.size()));

    @Test
    public void testInvalidArgumentsReachTheTool() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("discount", Map.of("percent", 2.7), toolResponse -> {
                    assertFalse(toolResponse.isError());
                    // The previous lenient conversion
                    assertEquals("percent=2", toolResponse.firstContent().asText().text());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        String discount(int percent) {
            return "percent=" + percent;
        }

    }

}
