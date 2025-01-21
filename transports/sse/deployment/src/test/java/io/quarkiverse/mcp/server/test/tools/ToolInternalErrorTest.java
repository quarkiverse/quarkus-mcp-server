package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ToolInternalErrorTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testError() throws URISyntaxException {
        initClient();
        JsonObject message = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "bravo")
                        .put("arguments", new JsonObject()
                                .put("price", 10)));
        send(message);
        JsonObject response = waitForLastResponse();
        assertEquals(JsonRPC.INTERNAL_ERROR, response.getJsonObject("error").getInteger("code"));
        assertEquals("Internal error", response.getJsonObject("error").getString("message"));
    }

    public static class MyTools {

        @Tool
        TextContent bravo(int price) {
            throw new NullPointerException();
        }

    }

}
