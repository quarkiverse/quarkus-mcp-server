package io.quarkiverse.mcp.server.test.streamablehttp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.InitialRequest.Transport;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ToolBatchStreamableTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testBatch() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.whenBatch()
                .toolsCall("bravo", Map.of("price", 10), r -> {
                    assertEquals("420", r.content().get(0).asText().text());
                })
                .toolsCall("bravo", Map.of("price", 100), r -> {
                    assertEquals("4200", r.content().get(0).asText().text());
                })
                .thenAssertResults();

    }

    public static class MyTools {

        @Tool
        String bravo(int price, McpConnection connection) {
            if (connection.initialRequest().transport() != Transport.STREAMABLE_HTTP) {
                throw new IllegalStateException();
            }
            return "" + price * 42;
        }
    }

}
