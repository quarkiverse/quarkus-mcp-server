package io.quarkiverse.mcp.server.test.streamablehttp;

import static io.quarkiverse.mcp.server.sse.runtime.StreamableHttpMcpMessageHandler.MCP_SESSION_ID_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.http.HttpRequest.BodyPublishers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.StreamableMcpSseClient;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ToolBatchStreamableTest extends StreamableHttpTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testBatch() {
        String mcpSessionId = initSession();

        JsonArray batch = new JsonArray();
        JsonObject msg1 = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "bravo")
                        .put("arguments", new JsonObject()
                                .put("price", 10)));
        batch.add(msg1);
        JsonObject msg2 = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "bravo")
                        .put("arguments", new JsonObject()
                                .put("price", 100)));
        batch.add(msg2);

        Map<String, String> headers = new HashMap<>(
                defaultHeaders().entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> e.getValue().toString())));
        headers.put(MCP_SESSION_ID_HEADER, mcpSessionId);

        StreamableMcpSseClient client = new StreamableMcpSseClient(messageEndpoint, BodyPublishers.ofString(batch.encode()),
                headers);
        // Send HTTP POST
        client.connect();
        List<JsonObject> responses = client.waitForResponses(2);
        assertResponse(responses, msg1, "420");
        assertResponse(responses, msg2, "4200");
    }

    private void assertResponse(List<JsonObject> responses, JsonObject msg, String expectedText) {
        JsonObject response = null;
        for (JsonObject r : responses) {
            if (r.getInteger("id") == msg.getInteger("id")) {
                response = r;
                break;
            }
        }
        assertNotNull(response);

        JsonObject result = assertResultResponse(msg, response);
        assertNotNull(result);
        assertFalse(result.getBoolean("isError"));
        JsonArray content = result.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals(expectedText, textContent.getString("text"));
    }

    public static class MyTools {

        @Tool
        String bravo(int price) {
            return "" + price * 42;
        }
    }

}
