package io.quarkiverse.mcp.server.test.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class LoggingTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testLog() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .toolsCall("charlie", Map.of("day", DayOfWeek.TUESDAY), response -> {
                    assertEquals("tuesday:INFO", response.content().get(0).asText().text());
                })
                .toolsCall("charlie", Map.of("day", DayOfWeek.MONDAY), response -> {
                    assertEquals("monday:INFO", response.content().get(0).asText().text());
                })
                .toolsCall("charlie", Map.of("day", DayOfWeek.WEDNESDAY), response -> {
                    assertEquals("wednesday:INFO", response.content().get(0).asText().text());
                })
                .thenAssertResults();

        List<JsonObject> notifications = client.waitForNotifications(2).notifications();
        assertLog(notifications.get(0), LogLevel.INFO, "tool:charlie", "Charlie does not work on MONDAY");
        assertLog(notifications.get(1), LogLevel.CRITICAL, "tool:charlie", "Wednesday is critical!");
    }

    private void assertLog(JsonObject log, LogLevel level, String logger, String message) {
        JsonObject params = log.getJsonObject("params");
        assertEquals(level.toString().toLowerCase(), params.getString("level"));
        assertEquals(logger, params.getString("logger"));
        assertEquals(message, params.getString("data"));
    }

}