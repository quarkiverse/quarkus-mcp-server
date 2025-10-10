package io.quarkiverse.mcp.server.websocket.test.tools;

import static io.quarkiverse.mcp.server.websocket.test.Checks.checkDuplicatedContext;
import static io.quarkiverse.mcp.server.websocket.test.Checks.checkExecutionModel;
import static io.quarkiverse.mcp.server.websocket.test.Checks.checkRequestContext;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpWebSocketTestClient;
import io.quarkiverse.mcp.server.websocket.test.Checks;
import io.quarkiverse.mcp.server.websocket.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ToolsCustomEndpointPathTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, Checks.class))
            .overrideConfigKey("quarkus.mcp.server.websocket.endpoint-path", "/foo");

    @Test
    public void testCustomEndpointPath() {
        McpWebSocketTestClient client = McpAssured.newWebSocketClient()
                .setEndpointPath("/foo")
                .build().connect();
        client.when()
                .toolsCall("bravo", Map.of("price", 12), toolResponse -> {
                    assertEquals("foo12", toolResponse.content().get(0).asText().text());
                })
                .toolsCall("bravo", Map.of("price", 21), toolResponse -> {
                    assertEquals("foo21", toolResponse.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        String bravo(int price) {
            checkExecutionModel(true);
            checkDuplicatedContext();
            checkRequestContext();
            return "foo" + price;
        }

    }

}
