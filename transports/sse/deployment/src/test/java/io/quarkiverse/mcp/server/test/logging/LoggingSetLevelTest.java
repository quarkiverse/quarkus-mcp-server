package io.quarkiverse.mcp.server.test.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.DayOfWeek;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class LoggingSetLevelTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testLog() {
        initClient();

        assertToolCall("monday:INFO", "charlie", DayOfWeek.MONDAY);

        List<JsonObject> notifications = client().waitForNotifications(1);
        assertLog(notifications.get(0), LogLevel.INFO, "tool:charlie", "Charlie does not work on MONDAY");

        JsonObject setLogLevelMessage = newMessage("logging/setLevel")
                .put("params", new JsonObject()
                        .put("level", LogLevel.CRITICAL.toString().toLowerCase()));
        send(setLogLevelMessage);

        assertToolCall("monday:CRITICAL", "charlie", DayOfWeek.MONDAY);
        assertToolCall("wednesday:CRITICAL", "charlie", DayOfWeek.WEDNESDAY);
        client().waitForNotifications(2);
    }

    private void assertLog(JsonObject log, LogLevel level, String logger, String message) {
        JsonObject params = log.getJsonObject("params");
        assertEquals(level.toString().toLowerCase(), params.getString("level"));
        assertEquals(logger, params.getString("logger"));
        assertEquals(message, params.getString("data"));
    }

    private void assertToolCall(String expectedText, String name, DayOfWeek day) {
        JsonObject toolGetMessage = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", name)
                        .put("arguments", new JsonObject().put("day", day)));
        send(toolGetMessage);

        JsonObject toolGetResponse = waitForLastResponse();

        JsonObject toolGetResult = assertResultResponse(toolGetMessage, toolGetResponse);
        assertNotNull(toolGetResult);
        JsonArray content = toolGetResult.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals(expectedText, textContent.getString("text"));
    }
}
