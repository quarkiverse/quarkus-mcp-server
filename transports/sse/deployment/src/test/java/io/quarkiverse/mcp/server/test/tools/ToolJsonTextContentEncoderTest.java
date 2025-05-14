package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ToolJsonTextContentEncoderTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, MyObject.class));

    @Test
    public void testEncoder() {
        initClient();
        assertToolCallResponse("bravo", 2);
        assertToolCallResponse("uni_bravo", 3);
        assertToolCallResponse("list_bravo", 4);
        assertToolCallResponse("uni_list_bravo", 5);
    }

    private void assertToolCallResponse(String name, int expectedSum) {
        JsonObject message = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", name)
                        .put("arguments", new JsonObject()
                                .put("price", 1)));
        send(message);
        JsonObject toolCallResponse = waitForLastResponse();
        JsonObject toolCallResult = assertResultResponse(message, toolCallResponse);
        assertNotNull(toolCallResult);
        assertFalse(toolCallResult.getBoolean("isError"));
        JsonArray content = toolCallResult.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        // Note that quotation marks in the original message must be escaped
        assertEquals("{\"name\":\"foo\",\"sum\":" + expectedSum + ",\"valid\":true}", textContent.getString("text"));
    }

    public record MyObject(String name, int sum, boolean valid) {

    }

    public static class MyTools {

        @Tool
        MyObject bravo(int price) {
            return new MyObject("foo", price * 2, true);
        }

        @Tool
        Uni<MyObject> uni_bravo(int price) {
            return Uni.createFrom().item(new MyObject("foo", price * 3, true));
        }

        @Tool
        List<MyObject> list_bravo(int price) {
            return List.of(new MyObject("foo", price * 4, true));
        }

        @Tool
        Uni<List<MyObject>> uni_list_bravo(int price) {
            return Uni.createFrom().item(List.of(new MyObject("foo", price * 5, true)));
        }

    }

}
