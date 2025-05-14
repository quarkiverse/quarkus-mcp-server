package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.FooService;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkiverse.mcp.server.test.Options;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ToolsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(FooService.class, Options.class, Checks.class, MyTools.class));

    @Test
    public void testTools() {
        initClient();
        JsonObject toolListMessage = newMessage("tools/list");
        send(toolListMessage);

        JsonObject toolListResponse = waitForLastResponse();

        JsonObject toolListResult = assertResultResponse(toolListMessage, toolListResponse);
        assertNotNull(toolListResult);
        JsonArray tools = toolListResult.getJsonArray("tools");
        assertEquals(8, tools.size());

        // alpha, bravo, charlie, list_charlie, uni_alpha, uni_bravo, uni_charlie, uni_list_charlie
        assertTool(tools, "alpha", null, schema -> {
            JsonObject properties = schema.getJsonObject("properties");
            assertEquals(1, properties.size());
            JsonObject priceProperty = properties.getJsonObject("price");
            assertNotNull(priceProperty);
            assertEquals("integer", priceProperty.getString("type"));
            assertEquals("Define the price...", priceProperty.getString("description"));
            assertTrue(schema.getJsonArray("required").isEmpty());
        });
        assertTool(tools, "uni_alpha", null, schema -> {
            JsonObject properties = schema.getJsonObject("properties");
            assertEquals(1, properties.size());
            JsonObject priceProperty = properties.getJsonObject("uni_price");
            assertNotNull(priceProperty);
            assertEquals("number", priceProperty.getString("type"));
            assertEquals(1, schema.getJsonArray("required").size());
            assertEquals("uni_price", schema.getJsonArray("required").getString(0));
        });
        assertTool(tools, "charlie", null, schema -> {
            JsonObject properties = schema.getJsonObject("properties");
            assertEquals(1, properties.size());
            JsonObject dayProperty = properties.getJsonObject("day");
            assertNotNull(dayProperty);
            assertEquals("string", dayProperty.getString("type"));
        });

        assertToolCall("Hello 1!", "alpha", new JsonObject()
                .put("price", 1));
        assertToolCall("Hello 1.0!", "uni_alpha", new JsonObject()
                .put("uni_price", 1));
        assertToolCall("Hello 1!", "bravo", new JsonObject()
                .put("price", 1));
        assertToolCall("Hello 1!", "uni_bravo", new JsonObject()
                .put("price", 1));
        assertToolCall("charlie1", "charlie", new JsonObject().put("day", DayOfWeek.FRIDAY.toString()));
        assertToolCall("charlie11", "charlie", new JsonObject().put("day", DayOfWeek.MONDAY.toString()));
        assertToolCall("charlie2", "uni_charlie", new JsonObject());
        assertToolCall("charlie3", "list_charlie", new JsonObject());
        assertToolCall("charlie4", "uni_list_charlie", new JsonObject());
    }

    private void assertToolCall(String expectedText, String name, JsonObject arguments) {
        JsonObject toolCallMessage = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", name)
                        .put("arguments", arguments));
        send(toolCallMessage);

        JsonObject toolCallResponse = waitForLastResponse();

        JsonObject toolCallResult = assertResultResponse(toolCallMessage, toolCallResponse);
        assertNotNull(toolCallResult);
        assertFalse(toolCallResult.getBoolean("isError"));
        JsonArray content = toolCallResult.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals(expectedText, textContent.getString("text"));
    }

}
