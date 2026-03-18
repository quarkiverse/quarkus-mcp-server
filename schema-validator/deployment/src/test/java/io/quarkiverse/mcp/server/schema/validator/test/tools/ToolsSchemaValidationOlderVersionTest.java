package io.quarkiverse.mcp.server.schema.validator.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.schema.validator.test.McpServerTest;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests schema validation with an older protocol version (2024-11-05) which uses
 * JSON Schema draft-07 and the "definitions" key (as opposed to the latest version
 * which uses JSON Schema 2020-12 and "$defs").
 * <p>
 * This exercises the schema draft detection and definitions key logic
 * in {@link io.quarkiverse.mcp.server.schema.validator.JsonSchemaValidator#newValidator}.
 */
public class ToolsSchemaValidationOlderVersionTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testToolsWithOlderProtocolVersion() {
        // Use "2024-11-05" which has "$schema": "http://json-schema.org/draft-07/schema#"
        // and uses "definitions" key instead of "$defs"
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setProtocolVersion("2024-11-05")
                .build()
                .connect();
        client.when()
                .toolsCall("bravo", Map.of("price", 42), toolResponse -> {
                    assertEquals("foo42", toolResponse.content().get(0).asText().text());
                })
                // Send a tools/call request without the required "params" field
                .message(client.newRequest("tools/call"))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertTrue(error.message().startsWith("Schema validation failed"));
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testToolsWithMiddleProtocolVersion() {
        // Use "2025-03-26" which also uses draft-07 and "definitions"
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setProtocolVersion("2025-03-26")
                .build()
                .connect();
        client.when()
                .toolsList(page -> {
                    assertEquals(1, page.size());
                    assertEquals("bravo", page.tools().get(0).name());
                })
                .toolsCall("bravo", Map.of("price", 7), toolResponse -> {
                    assertEquals("foo7", toolResponse.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        String bravo(int price) {
            return "foo" + price;
        }
    }
}
