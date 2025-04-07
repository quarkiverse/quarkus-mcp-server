package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class NumberArgumentTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testError() {
        initClient();
        JsonObject message = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "get_list_of_strings")
                        .put("arguments", new JsonObject()
                                .put("id", 10)
                                .put("alpha", 1L)
                                .put("bravo", 10.1)
                                .put("delta", 20.1)
                                .put("charlie", 11)
                                .put("echo", 42.1)));
        send(message);
        JsonObject response = waitForLastResponse();
        JsonObject result = assertResponseMessage(message, response);
        assertNotNull(result);
        assertFalse(result.getBoolean("isError"));
        JsonArray content = result.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals("10:1:10.1:20.1:11:42.1", textContent.getString("text"));
    }

    public static class MyTools {

        @Tool(name = "get_list_of_strings")
        public String getListOfStrings(Long id, short alpha, Float bravo, Double delta, Byte charlie, Optional<Double> echo) {
            return id + ":" + alpha + ":" + bravo + ":" + delta + ":" + charlie + ":" + echo.orElse(1.0);
        }
    }
}
