package io.quarkiverse.mcp.server.schema.validator.test.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.schema.validator.test.McpServerTest;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class LoggingSchemaValidationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testLoggingSetLevel() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                // Valid logging/setLevel request
                .message(client.newRequest(McpMethod.LOGGING_SET_LEVEL)
                        .put("params", new JsonObject().put("level", LogLevel.INFO.toString().toLowerCase())))
                .send()
                // Send a logging/setLevel request without required "params" field
                .message(client.newRequest(McpMethod.LOGGING_SET_LEVEL))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertTrue(error.message().startsWith("Schema validation failed"));
                })
                .send()
                // Send a logging/setLevel request with missing required "level" field;
                // the error message should contain the schema name "SetLevelRequest"
                .message(client.newRequest(McpMethod.LOGGING_SET_LEVEL)
                        .put("params", new JsonObject()))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertTrue(error.message().contains("SetLevelRequest"));
                })
                .send()
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        String dummy() {
            return "dummy";
        }
    }
}
