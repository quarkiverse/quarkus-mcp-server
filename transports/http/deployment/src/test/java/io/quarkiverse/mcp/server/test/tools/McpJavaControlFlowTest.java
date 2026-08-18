package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.WrapBusinessError;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class McpJavaControlFlowTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testMcpJavaOperationCancelledNeverWrapped() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "juliet"));
        client.sendAndForget(request);

        Awaitility.await()
                .atMost(2, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertEquals(1, client.snapshot().responses().size());
                });
    }

    public static class MyTools {

        @WrapBusinessError(Exception.class)
        @Tool
        String juliet() {
            throw new org.mcpjava.server.Cancellation.OperationCancelledException();
        }
    }
}
