package io.quarkiverse.mcp.server.test.rawmessage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.RawMessage;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.runtime.Startup;
import io.quarkus.test.QuarkusUnitTest;

public class RawMessageTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testRawMessage() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("foo", Map.of("count", 2), toolResponse -> {
                    assertEquals("foofoofoofoo", toolResponse.content().get(0).asText().text());
                })
                .toolsCall("baz", Map.of("count", 3), toolResponse -> {
                    assertEquals("bazbazbazbazbazbazbazbazbaz", toolResponse.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool(description = "Bar")
        String foo(RawMessage message, int count) {
            // A raw message looks like:
            // {
            //  "jsonrpc" : "2.0",
            //  "method" : "tools/call",
            //  "id" : 2,
            //  "params" : {
            //    "name" : "foo",
            //    "arguments" : {
            //      "count" : 2
            //    }
            //   }
            // }
            int rawCount = message.asJsonObject()
                    .getJsonObject("params")
                    .getJsonObject("arguments")
                    .getInteger("count");
            return "foo".repeat(count * rawCount);
        }

        @Inject
        ToolManager toolManager;

        @Startup
        void onStart() {
            toolManager.newTool("baz")
                    .setDescription("Barbar")
                    .setHandler(toolArgs -> {
                        int rawCount = toolArgs.rawMessage()
                                .asJsonObject()
                                .getJsonObject("params")
                                .getJsonObject("arguments")
                                .getInteger("count");
                        return ToolResponse
                                .success("baz".repeat(rawCount * Integer.parseInt(toolArgs.args().get("count").toString())));
                    })
                    .register();
        }

    }
}
