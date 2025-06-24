package io.quarkiverse.mcp.server.test.logging;

import static io.quarkiverse.mcp.server.sse.runtime.StreamableHttpMcpMessageHandler.MCP_SESSION_ID_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.http.HttpRequest.BodyPublishers;
import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.test.StreamableHttpTest;
import io.quarkiverse.mcp.server.test.StreamableMcpSseClient;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class LoggingStreamableTest extends StreamableHttpTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testLog() {
        String mcpSessionId = initSession();

        Map<String, String> headers = new HashMap<>();
        headers.put(MCP_SESSION_ID_HEADER, mcpSessionId);
        defaultHeaders().entrySet().stream().forEach(e -> headers.put(e.getKey(), e.getValue().toString()));

        JsonObject charlie1 = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "charlie")
                        .put("arguments", new JsonObject().put("day", DayOfWeek.MONDAY)));

        StreamableMcpSseClient client = new StreamableMcpSseClient(messageEndpoint, BodyPublishers.ofString(charlie1.encode()),
                headers);
        client.connect();
        List<JsonObject> notifications1 = client.waitForNotifications(1);
        assertEquals(1, notifications1.size());
        assertLog(notifications1.get(0), LogLevel.INFO, "tool:charlie", "Charlie does not work on MONDAY");

        JsonObject response1 = client.waitForResponse(charlie1);
        assertResponse(charlie1, response1, "monday:INFO");

        JsonObject charlie2 = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "charlie")
                        .put("arguments", new JsonObject().put("day", DayOfWeek.WEDNESDAY)));

        StreamableMcpSseClient client2 = new StreamableMcpSseClient(messageEndpoint, BodyPublishers.ofString(charlie2.encode()),
                headers);
        client2.connect();
        List<JsonObject> notifications2 = client2.waitForNotifications(1);
        assertEquals(1, notifications2.size());
        assertLog(notifications2.get(0), LogLevel.CRITICAL, "tool:charlie", "Wednesday is critical!");

        JsonObject response2 = client2.waitForResponse(charlie2);
        assertResponse(charlie2, response2, "wednesday:INFO");
    }

    private void assertLog(JsonObject log, LogLevel level, String logger, String message) {
        JsonObject params = log.getJsonObject("params");
        assertEquals(level.toString().toLowerCase(), params.getString("level"));
        assertEquals(logger, params.getString("logger"));
        assertEquals(message, params.getString("data"));
    }

    private void assertResponse(JsonObject request, JsonObject response, String expectedText) {
        JsonObject result = assertResultResponse(request, response);
        assertNotNull(result);
        JsonArray content = result.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals(expectedText, textContent.getString("text"));
    }
}
