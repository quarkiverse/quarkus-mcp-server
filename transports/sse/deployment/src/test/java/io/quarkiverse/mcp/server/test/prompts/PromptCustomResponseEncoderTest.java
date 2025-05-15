package io.quarkiverse.mcp.server.test.prompts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.PromptResponseEncoder;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class PromptCustomResponseEncoderTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyPrompts.class, MyObject.class, MyObjectEncoder.class));

    @Test
    public void testEncoder() {
        initClient();
        JsonObject message = newMessage("prompts/get")
                .put("params", new JsonObject()
                        .put("name", "bravo")
                        .put("arguments", new JsonObject()
                                .put("price", "10")));
        send(message);
        JsonObject response = waitForLastResponse();
        JsonObject result = assertResultResponse(message, response);
        assertNotNull(result);
        JsonArray messages = result.getJsonArray("messages");
        assertEquals(1, messages.size());
        JsonObject m = messages.getJsonObject(0);
        assertEquals("user", m.getString("role"));
        JsonObject content = m.getJsonObject("content");
        assertEquals("text", content.getString("type"));
        assertEquals("MyObject[name=foo, sum=20, valid=true]", content.getString("text"));
    }

    public record MyObject(String name, int sum, boolean valid) {

    }

    public static class MyPrompts {

        @Prompt
        MyObject bravo(String price) {
            return new MyObject("foo", Integer.parseInt(price) * 2, true);
        }

    }

    @Singleton
    @Priority(1)
    public static class MyObjectEncoder implements PromptResponseEncoder<MyObject> {

        @Override
        public boolean supports(Class<?> runtimeType) {
            return MyObject.class.equals(runtimeType);
        }

        @Override
        public PromptResponse encode(MyObject value) {
            return new PromptResponse(null, List.of(PromptMessage.withUserRole(new TextContent(value.toString()))));
        }

    }

}
