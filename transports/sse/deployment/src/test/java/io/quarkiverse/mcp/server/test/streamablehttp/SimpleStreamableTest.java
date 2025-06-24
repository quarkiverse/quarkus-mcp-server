package io.quarkiverse.mcp.server.test.streamablehttp;

import static io.quarkiverse.mcp.server.sse.runtime.StreamableHttpMcpMessageHandler.MCP_SESSION_ID_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.FooService;
import io.quarkiverse.mcp.server.test.Options;
import io.quarkiverse.mcp.server.test.StreamableHttpTest;
import io.quarkiverse.mcp.server.test.tools.MyTools;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class SimpleStreamableTest extends StreamableHttpTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(FooService.class, Options.class, Checks.class, MyTools.class));

    @Inject
    ToolManager toolManager;

    @Test
    public void testTools() {
        String mcpSessionId = initSession();

        // Just test that registration does not fail even if notification is not sent
        toolManager.newTool("foofoo")
                .setDescription("foofoo")
                .setHandler(atgs -> null)
                .register();
        assertNotNull(toolManager.getTool("foofoo"));
        assertNotNull(toolManager.removeTool("foofoo"));

        JsonObject toolListMessage = newMessage("tools/list");
        ValidatableResponse response = send(toolListMessage, Map.of(MCP_SESSION_ID_HEADER, mcpSessionId));

        JsonObject toolListResponse = new JsonObject(response.extract().asString());

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
                .put("price", 1), mcpSessionId);
        assertToolCall("Hello 1.0!", "uni_alpha", new JsonObject()
                .put("uni_price", 1), mcpSessionId);
        assertToolCall("Hello 1!", "bravo", new JsonObject()
                .put("price", 1), mcpSessionId);
        assertToolCall("Hello 1!", "uni_bravo", new JsonObject()
                .put("price", 1), mcpSessionId);
        assertToolCall("charlie1", "charlie", new JsonObject().put("day", DayOfWeek.FRIDAY.toString()), mcpSessionId);
        assertToolCall("charlie11", "charlie", new JsonObject().put("day", DayOfWeek.MONDAY.toString()), mcpSessionId);
        assertToolCall("charlie2", "uni_charlie", new JsonObject(), mcpSessionId);
        assertToolCall("charlie3", "list_charlie", new JsonObject(), mcpSessionId);
        assertToolCall("charlie4", "uni_list_charlie", new JsonObject(), mcpSessionId);
    }

    private void assertToolCall(String expectedText, String name, JsonObject arguments, String mcpSessionId) {
        JsonObject toolCallMessage = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", name)
                        .put("arguments", arguments));
        ValidatableResponse response = send(toolCallMessage, Map.of(MCP_SESSION_ID_HEADER, mcpSessionId));

        JsonObject toolCallResponse = new JsonObject(response.extract().asString());

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
