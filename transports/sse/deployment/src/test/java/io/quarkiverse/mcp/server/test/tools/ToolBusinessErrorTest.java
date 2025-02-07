package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.WrapBusinessError;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ToolBusinessErrorTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testError() throws URISyntaxException {
        initClient();
        assertBusinessError("bravo", new JsonObject().put("price", 10), "Business error");
        assertBusinessError("charlie", new JsonObject(), "java.lang.IllegalArgumentException: I am not ready!");
        assertBusinessError("delta", new JsonObject(), "java.lang.NullPointerException: I am null!");
    }

    private void assertBusinessError(String toolName, JsonObject arguments, String expectedErrorText) {
        JsonObject message = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", toolName)
                        .put("arguments", arguments));
        send(message);
        JsonObject toolCallResponse = waitForLastResponse();
        JsonObject toolCallResult = assertResponseMessage(message, toolCallResponse);
        assertNotNull(toolCallResult);
        assertTrue(toolCallResult.getBoolean("isError"));
        JsonArray content = toolCallResult.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals(expectedErrorText, textContent.getString("text"));
    }

    public static class MyTools {

        @Tool
        TextContent bravo(int price) {
            throw new ToolCallException("Business error");
        }

        @WrapBusinessError
        @Tool
        String charlie() {
            throw new IllegalArgumentException("I am not ready!");
        }

        @WrapBusinessError(NullPointerException.class)
        @Tool
        Uni<String> delta() {
            return Uni.createFrom().failure(new NullPointerException("I am null!"));
        }

    }

}
