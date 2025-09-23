package io.quarkiverse.mcp.server.hibernate.validator.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ToolInfo;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ToolConstraintViolationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testError() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .toolsCall("bravo")
                .withArguments(Map.of("price", 1))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, error.code());
                    assertEquals("bravo.price: must be greater than or equal to 5", error.message());
                })
                .send()
                .toolsCall("charlie")
                .withArguments(Map.of("price", 1, "name", ""))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, error.code());
                    assertTrue(error.message().contains("charlie.price: must be greater than or equal to 5"), error.toString());
                    assertTrue(error.message().contains("charlie.name: must not be blank"), error.toString());
                })
                .send()
                .toolsCall("delta")
                .withArguments(Map.of("person", Map.of("age", 5)))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, error.code());
                    assertEquals("delta.person.age: must be greater than or equal to 10", error.message());
                })
                .send()
                .toolsList(page -> {
                    assertEquals(3, page.tools().size());
                    ToolInfo bravo = page.findByName("bravo");
                    JsonObject bravoSchema = bravo.inputSchema();
                    assertPropertyHasMinimum(bravoSchema.getJsonObject("properties"), "price", 5);
                })
                .thenAssertResults();
    }

    private void assertPropertyHasMinimum(JsonObject properties, String name, int expectedMinimum) {
        JsonObject property = properties.getJsonObject(name);
        assertEquals(expectedMinimum, property.getInteger("minimum"));
    }

    public static class MyTools {

        @Tool
        // https://github.com/quarkusio/quarkus/issues/50225
        TextContent bravo(@Min(5) Integer price) {
            throw new ToolCallException("Business error");
        }

        @Tool
        // https://github.com/quarkusio/quarkus/issues/50225
        TextContent charlie(@Min(5) Integer price, @NotBlank String name) {
            throw new ToolCallException("Business error");
        }

        @Tool
        TextContent delta(@Valid Person person) {
            throw new ToolCallException("Business error");
        }

    }

    public record Person(@Min(10) int age) {
    }

}
