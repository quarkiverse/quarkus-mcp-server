package io.quarkiverse.mcp.server.test.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class LoggingSetLevelTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testLog() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();

        client.when()
                .message(client.newRequest(McpMethod.LOGGING_SET_LEVEL)
                        .put("params", new JsonObject()))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertEquals("Log level not set", error.message());
                })
                .send()
                .message(client.newRequest(McpMethod.LOGGING_SET_LEVEL)
                        .put("params", new JsonObject().put("level", "alpha")))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertEquals("Invalid log level set: alpha", error.message());
                })
                .send()
                .thenAssertResults();

        client.when()
                .toolsCall("charlie", Map.of("day", DayOfWeek.MONDAY), response -> {
                    assertEquals("monday:INFO", response.content().get(0).asText().text());
                })
                .thenAssertResults();

        List<JsonObject> notifications = client.waitForNotifications(1).notifications();

        assertLog(notifications.get(0), LogLevel.INFO, "tool:charlie", "Charlie does not work on MONDAY");

        JsonObject setLogLevelMessage = client.newRequest(McpMethod.LOGGING_SET_LEVEL)
                .put("params", new JsonObject()
                        .put("level", LogLevel.CRITICAL.toString().toLowerCase()));
        client.when()
                .message(setLogLevelMessage).send().thenAssertResults();

        client.when()
                .toolsCall("charlie", Map.of("day", DayOfWeek.MONDAY), response -> {
                    assertEquals("monday:CRITICAL", response.content().get(0).asText().text());
                })
                .toolsCall("charlie", Map.of("day", DayOfWeek.WEDNESDAY), response -> {
                    assertEquals("wednesday:CRITICAL", response.content().get(0).asText().text());
                })
                .thenAssertResults();

        client.waitForNotifications(2);
    }

    private void assertLog(JsonObject log, LogLevel level, String logger, String message) {
        JsonObject params = log.getJsonObject("params");
        assertEquals(level.toString().toLowerCase(), params.getString("level"));
        assertEquals(logger, params.getString("logger"));
        assertEquals(message, params.getString("data"));
    }

}
