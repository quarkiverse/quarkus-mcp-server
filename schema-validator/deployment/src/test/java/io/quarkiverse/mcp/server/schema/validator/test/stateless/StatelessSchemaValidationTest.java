package io.quarkiverse.mcp.server.schema.validator.test.stateless;

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
import io.vertx.core.json.JsonObject;

/**
 * Tests schema validation with the stateless protocol version (2026-07-28) which uses
 * JSON Schema 2020-12 and the "$defs" key, and where each request must carry per-request
 * metadata (protocol version, client info and client capabilities) in its {@code params._meta}.
 * <p>
 * This exercises the loading of the {@code mcp_schema_2026-07-28.json} resource by
 * {@link io.quarkiverse.mcp.server.schema.validator.JsonSchemaValidator#newValidator}.
 */
public class StatelessSchemaValidationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testStatelessSchemaValidation() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        // A well-formed stateless tools/call (valid "name" so the transport routes it), but with
        // an invalid "arguments" type - it must be an object, not a string.
        // injectStatelessMeta() adds the per-request _meta so the message is routed as stateless.
        JsonObject invalidToolsCall = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "bravo")
                        .put("arguments", "not-an-object"));
        McpAssured.injectStatelessMeta(invalidToolsCall);

        // A well-formed stateless tools/list, but with an invalid "cursor" type (must be a string).
        JsonObject invalidToolsList = client.newRequest("tools/list")
                .put("params", new JsonObject().put("cursor", true));
        McpAssured.injectStatelessMeta(invalidToolsList);

        client.when()
                // A regular stateless call carries the required params._meta and passes validation
                .toolsCall("bravo", Map.of("price", 42), toolResponse -> {
                    assertEquals("foo42", toolResponse.firstContent().asText().text());
                })
                .toolsList(page -> {
                    assertEquals(1, page.size());
                    assertEquals("bravo", page.tools().get(0).name());
                })
                .message(invalidToolsCall)
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertTrue(error.message().startsWith("Schema validation failed"));
                })
                .send()
                .message(invalidToolsList)
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertTrue(error.message().startsWith("Schema validation failed"));
                })
                .send()
                .thenAssertResults();

        client.disconnect();
    }

    public static class MyTools {

        @Tool
        String bravo(int price) {
            return "foo" + price;
        }
    }
}
