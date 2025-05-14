package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class OptionalListStringInputSchemaTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(1000)
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testInputSchema() {
        initClient();
        JsonObject toolListMessage = newMessage("tools/list");
        send(toolListMessage);

        JsonObject toolListResponse = waitForLastResponse();

        JsonObject toolListResult = assertResultResponse(toolListMessage, toolListResponse);
        assertNotNull(toolListResult);
        JsonArray tools = toolListResult.getJsonArray("tools");
        assertEquals(2, tools.size());

        JsonObject runOptionalTasks = tools.getJsonObject(0);
        assertEquals(
                "{\"type\":\"object\",\"properties\":{\"tasks\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},\"required\":[]}",
                runOptionalTasks.getJsonObject("inputSchema").toString());

        JsonObject runTasks = tools.getJsonObject(1);
        assertEquals(
                "{\"type\":\"object\",\"properties\":{\"tasks\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},\"required\":[\"tasks\"]}",
                runTasks.getJsonObject("inputSchema").toString());
    }

    public static class MyTools {

        @Tool
        public String runTasks(List<String> tasks) {
            return "ok";
        }

        @Tool
        public String runOptionalTasks(Optional<List<String>> tasks) {
            return "ok";
        }

    }

}
