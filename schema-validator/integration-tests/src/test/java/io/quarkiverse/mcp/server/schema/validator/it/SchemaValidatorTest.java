package io.quarkiverse.mcp.server.schema.validator.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SchemaValidatorTest {

    @Test
    void testSchemaValidation() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                // A valid request passes schema validation (this loads the mcp_schema_*.json resource,
                // which must be registered for native mode - see issue #1111)
                .toolsCall("bravo", Map.of("price", 12), toolResponse -> {
                    assertEquals("foo12", toolResponse.firstContent().asText().text());
                })
                // An invalid request (missing the required "params" field) is rejected by the validator
                .message(client.newRequest("tools/call"))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertTrue(error.message().startsWith("Schema validation failed"));
                })
                .send()
                .thenAssertResults();
    }

}
