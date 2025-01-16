package io.quarkiverse.mcp.server.test.logging;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.DayOfWeek;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class LoggingTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testLog() throws URISyntaxException {
        URI endpoint = initClient();

        assertToolCall("tuesday:INFO", endpoint, "charlie", DayOfWeek.TUESDAY);
        assertToolCall("monday:INFO", endpoint, "charlie", DayOfWeek.MONDAY);
        assertToolCall("wednesday:INFO", endpoint, "charlie", DayOfWeek.WEDNESDAY);

        List<JsonObject> notifications = client().waitForNotifications(2);
        assertLog(notifications.get(0), LogLevel.INFO, "tool:charlie", "Charlie does not work on MONDAY");
        assertLog(notifications.get(1), LogLevel.CRITICAL, "tool:charlie", "Wednesday is critical!");
    }

    private void assertLog(JsonObject log, LogLevel level, String logger, String message) {
        JsonObject params = log.getJsonObject("params");
        assertEquals(level.toString().toLowerCase(), params.getString("level"));
        assertEquals(logger, params.getString("logger"));
        assertEquals(message, params.getString("data"));
    }

    private void assertToolCall(String expectedText, URI endpoint, String name, DayOfWeek day) {
        JsonObject toolGetMessage = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", name)
                        .put("arguments", new JsonObject().put("day", day)));

        given()
                .contentType(ContentType.JSON)
                .when()
                .body(toolGetMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200);

        JsonObject toolGetResponse = waitForLastResponse();

        JsonObject toolGetResult = assertResponseMessage(toolGetMessage, toolGetResponse);
        assertNotNull(toolGetResult);
        JsonArray content = toolGetResult.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals(expectedText, textContent.getString("text"));
    }
}
