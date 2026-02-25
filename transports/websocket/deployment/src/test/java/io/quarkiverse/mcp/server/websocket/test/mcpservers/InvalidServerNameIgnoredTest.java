package io.quarkiverse.mcp.server.websocket.test.mcpservers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.websocket.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class InvalidServerNameIgnoredTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.invalid-server-name-strategy", "ignore");

    @Test
    public void test() {
        McpAssured.newConnectedWebSocketClient()
                .when()
                .toolsCall("bravo")
                .withErrorAssert(error -> {
                    assertEquals("Invalid tool name: bravo", error.message());
                })
                .send()
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        @McpServer("bravo")
        String bravo() {
            return "2";
        }

    }

}
