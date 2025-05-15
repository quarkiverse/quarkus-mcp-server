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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ToolComplexArgumentTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testError() {
        initClient();
        assertResult("alpha",
                new JsonObject().put("myArg",
                        new JsonObject()
                                .put("price", 10)
                                .put("names", new JsonArray().add("foo")
                                        .add("bar"))),
                "MyArg[price=10, names=[foo, bar]]");

        assertResult("alphas",
                new JsonObject().put("myArgs",
                        new JsonArray().add(new JsonObject()
                                .put("price", 10)
                                .put("names", new JsonArray().add("foo")
                                        .add("bar")))),
                "[MyArg[price=10, names=[foo, bar]]]");
    }

    private void assertResult(String toolName, JsonObject arguments, String expectedErrorText) {
        JsonObject message = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", toolName)
                        .put("arguments", arguments));
        send(message);
        JsonObject toolCallResponse = waitForLastResponse();
        JsonObject toolCallResult = assertResultResponse(message, toolCallResponse);
        assertNotNull(toolCallResult);
        assertFalse(toolCallResult.getBoolean("isError"));
        JsonArray content = toolCallResult.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals(expectedErrorText, textContent.getString("text"));
    }

    public static class MyTools {

        @Tool
        String alpha(MyArg myArg) {
            return myArg.toString();
        }

        @Tool
        String alphas(List<MyArg> myArgs) {
            return myArgs.toString();
        }

        public record MyArg(int price, List<String> names) {

        }

    }

}
