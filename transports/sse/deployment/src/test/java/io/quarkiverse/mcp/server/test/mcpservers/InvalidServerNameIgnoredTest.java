package io.quarkiverse.mcp.server.test.mcpservers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class InvalidServerNameIgnoredTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.invalid-server-name-strategy", "ignore");

    @Test
    public void test() {
        initClient();
        JsonObject msg1 = newToolCallMessage("bravo");
        send(msg1);
        JsonObject r1 = client().waitForResponse(msg1);
        JsonObject error1 = assertErrorResponse(msg1, r1);
        assertEquals("Invalid tool name: bravo", error1.getString("message"));
    }

    public static class MyTools {

        @Tool
        @McpServer("bravo")
        String bravo() {
            return "2";
        }

    }

}
