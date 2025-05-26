package io.quarkiverse.mcp.server.test.devmode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusDevModeTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ToolChangeTest extends McpServerTest {

    @RegisterExtension
    final static QuarkusDevModeTest test = new QuarkusDevModeTest()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testChange() {
        initClient();

        JsonObject message = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "bravo")
                        .put("arguments", new JsonObject().put("price", 10)));
        send(message);
        JsonObject toolCallResponse = waitForLastResponse();
        JsonObject toolCallResult = assertResultResponse(message, toolCallResponse);
        assertNotNull(toolCallResult);
        assertTrue(toolCallResult.getBoolean("isError"));
        JsonArray content = toolCallResult.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals("Business error", textContent.getString("text"));

        test.modifySourceFile(MyTools.class, new Function<String, String>() {
            @Override
            public String apply(String source) {
                return source.replace("throw new ToolCallException(\"Business error\");", "return \"\" + price * 42;");
            }
        });

        // re-init the client
        initClient();

        message = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "bravo")
                        .put("arguments", new JsonObject().put("price", 10)));
        send(message);
        toolCallResponse = waitForLastResponse();
        toolCallResult = assertResultResponse(message, toolCallResponse);
        assertNotNull(toolCallResult);
        assertFalse(toolCallResult.getBoolean("isError"));
        content = toolCallResult.getJsonArray("content");
        assertEquals(1, content.size());
        textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals("420", textContent.getString("text"));
    }

}
