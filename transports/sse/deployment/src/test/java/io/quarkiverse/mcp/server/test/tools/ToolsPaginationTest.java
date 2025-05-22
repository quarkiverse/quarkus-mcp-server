package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ToolsPaginationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.mcp.server.tools.page-size", "3");

    @Inject
    ToolManager manager;

    @Test
    public void testTools() {
        int loop = 8;
        for (int i = 1; i <= loop; i++) {
            String name = i + "";
            manager.newTool(name)
                    .setDescription(name)
                    .setHandler(
                            args -> ToolResponse.success("Result " + name))
                    .register();
        }

        Instant lastCreatedAt = Instant.EPOCH;
        for (ToolInfo info : manager) {
            assertTrue(info.createdAt().isAfter(lastCreatedAt));
            lastCreatedAt = info.createdAt();
        }

        initClient();

        JsonObject message = newMessage("tools/list");
        send(message);

        JsonObject response = waitForLastResponse();
        JsonObject result = assertResultResponse(message, response);
        assertNotNull(result);
        JsonArray tools = result.getJsonArray("tools");
        assertEquals(3, tools.size());
        String cursor = result.getString("nextCursor");
        assertNotNull(cursor);

        assertTool(tools.getJsonObject(0), "1");
        assertTool(tools.getJsonObject(1), "2");
        assertTool(tools.getJsonObject(2), "3");

        message = newMessage("tools/list").put("params", new JsonObject().put("cursor", cursor));
        send(message);

        response = waitForLastResponse();
        result = assertResultResponse(message, response);
        assertNotNull(result);
        tools = result.getJsonArray("tools");
        assertEquals(3, tools.size());
        cursor = result.getString("nextCursor");
        assertNotNull(cursor);

        assertTool(tools.getJsonObject(0), "4");
        assertTool(tools.getJsonObject(1), "5");
        assertTool(tools.getJsonObject(2), "6");

        message = newMessage("tools/list").put("params", new JsonObject().put("cursor", cursor));
        send(message);

        response = waitForLastResponse();
        result = assertResultResponse(message, response);
        assertNotNull(result);
        tools = result.getJsonArray("tools");
        assertEquals(2, tools.size());
        assertNull(result.getString("nextCursor"));

        assertTool(tools.getJsonObject(0), "7");
        assertTool(tools.getJsonObject(1), "8");
    }

    private void assertTool(JsonObject tool, String name) {
        assertEquals(name, tool.getString("name"));
        assertEquals(name, tool.getString("description"));
    }

}
