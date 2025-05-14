package io.quarkiverse.mcp.server.test.batch;

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

public class ToolBatchTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testBatchMessage() {
        initClient();
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
        send(batch.encode());

        List<JsonObject> responses = client().waitForResponses(3);
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
