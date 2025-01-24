package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.util.List;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.ToolResponseEncoder;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ToolCustomResponseEncoderTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, MyObject.class, MyObjectEncoder.class));

    @Test
    public void testEncoder() throws URISyntaxException {
        initClient();
        JsonObject message = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "bravo")
                        .put("arguments", new JsonObject()
                                .put("price", 10)));
        send(message);
        JsonObject toolCallResponse = waitForLastResponse();
        JsonObject toolCallResult = assertResponseMessage(message, toolCallResponse);
        assertNotNull(toolCallResult);
        assertTrue(toolCallResult.getBoolean("isError"));
        JsonArray content = toolCallResult.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals("MyObject[name=foo, sum=20, valid=true]", textContent.getString("text"));
    }

    public record MyObject(String name, int sum, boolean valid) {

    }

    public static class MyTools {

        @Tool
        MyObject bravo(int price) {
            return new MyObject("foo", price * 2, true);
        }

    }

    @Singleton
    @Priority(1)
    public static class MyObjectEncoder implements ToolResponseEncoder<MyObject> {

        @Override
        public boolean supports(Class<?> runtimeType) {
            return MyObject.class.equals(runtimeType);
        }

        @Override
        public ToolResponse encode(MyObject value) {
            return new ToolResponse(true, List.of(new TextContent(value.toString())));
        }

    }

}
