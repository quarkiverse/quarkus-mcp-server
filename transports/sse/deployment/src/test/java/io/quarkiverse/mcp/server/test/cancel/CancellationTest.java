package io.quarkiverse.mcp.server.test.cancel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.Cancellation.OperationCancellationException;
import io.quarkiverse.mcp.server.Cancellation.Result;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class CancellationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testCancellation() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "bravo"));
        client.sendAndForget(request);

        JsonObject notification = client.newMessage("notifications/cancelled").put("params",
                new JsonObject()
                        .put("requestId", request.getValue("id"))
                        .put("reason", "No reason at all"));

        client.sendAndForget(notification);
        // This notification should be ignored
        client.sendAndForget(notification);

        Awaitility.await().until(() -> MyTools.CANCELLED.get());
        // Only the response to the "initialize" request
        assertEquals(1, client.snapshot().responses().size());
    }

    public static class MyTools {

        static final AtomicBoolean CANCELLED = new AtomicBoolean();

        @Tool
        String bravo(Cancellation cancellation, @ToolArg(defaultValue = "1") int price) throws InterruptedException {
            int c = 0;
            while (c++ < 20) {
                Result r = cancellation.check();
                if (r.isRequested()
                        && r.reason().isPresent()
                        && r.reason().get().equals("No reason at all")) {
                    CANCELLED.set(true);
                    throw new OperationCancellationException();
                }
                TimeUnit.MILLISECONDS.sleep(200);
            }
            return "OK";
        }

    }

}
