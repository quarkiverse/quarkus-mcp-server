package io.quarkiverse.mcp.server.test.cancel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.Cancellation.OperationCancellationException;
import io.quarkiverse.mcp.server.Cancellation.Result;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.runtime.Startup;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class CancellationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testCancellation() throws InterruptedException {
        assertCancellation("alpha", MyTools.ALPHA_LATCH);
        assertCancellation("bravo", MyTools.BRAVO_LATCH);
        assertCancellation("charlie", MyTools.CHARLIE_LATCH);
        assertEquals("No reason at all", MyTools.CANCEL_REASON.get().orElse(null));
        assertCancellationMultipleActions();
        assertEquals("No reason at all", MyTools.CANCEL_REASON.get().orElse(null));
    }

    @Test
    public void testOnCancelledNullAction() {
        McpAssured.newConnectedStreamableClient()
                .when()
                .toolsCall("echo", r -> assertEquals("REJECTED", r.firstContent().asText().text()))
                .thenAssertResults();
    }

    private void assertCancellationMultipleActions() throws InterruptedException {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        MyTools.CANCELLED.set(false);
        MyTools.CANCEL_ACTION_COUNT.set(0);
        MyTools.CANCEL_REASON.set(null);

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "delta"));
        client.sendAndForget(request);

        // Wait for the tool execution start
        assertTrue(MyTools.DELTA_LATCH.await(5, TimeUnit.SECONDS));

        JsonObject notification = client.newMessage("notifications/cancelled").put("params",
                new JsonObject()
                        .put("requestId", request.getValue("id"))
                        .put("reason", "No reason at all"));
        client.sendAndForget(notification);

        Awaitility.await().until(() -> MyTools.CANCELLED.get());
        // Both actions should have been invoked
        assertEquals(2, MyTools.CANCEL_ACTION_COUNT.get());
        // Only the response to the "initialize" request
        assertEquals(1, client.snapshot().responses().size());
    }

    private void assertCancellation(String toolName, CountDownLatch latch) throws InterruptedException {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        MyTools.CANCELLED.set(false);
        MyTools.CANCEL_REASON.set(null);

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", toolName));
        client.sendAndForget(request);

        // Wait for the tool execution start
        assertTrue(latch.await(5, TimeUnit.SECONDS));

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

        static final CountDownLatch ALPHA_LATCH = new CountDownLatch(1);
        static final CountDownLatch BRAVO_LATCH = new CountDownLatch(1);
        static final CountDownLatch CHARLIE_LATCH = new CountDownLatch(1);
        static final CountDownLatch DELTA_LATCH = new CountDownLatch(1);

        static final AtomicBoolean CANCELLED = new AtomicBoolean();
        static final AtomicInteger CANCEL_ACTION_COUNT = new AtomicInteger();
        static final AtomicReference<Optional<String>> CANCEL_REASON = new AtomicReference<>();

        @Inject
        ToolManager manager;

        @Startup
        void onStart() {
            manager.newTool("alpha")
                    .setDescription("alpha description!")
                    .setHandler(
                            toolArgs -> {
                                ALPHA_LATCH.countDown();
                                int c = 0;
                                while (c++ < 20) {
                                    Result r = toolArgs.cancellation().check();
                                    if (r.isRequested()
                                            && r.reason().isPresent()
                                            && r.reason().get().equals("No reason at all")) {
                                        CANCELLED.set(true);
                                        throw new OperationCancellationException();
                                    }
                                    try {
                                        TimeUnit.MILLISECONDS.sleep(500);
                                    } catch (InterruptedException e) {
                                        throw new RuntimeException();
                                    }
                                }
                                return ToolResponse.success("OK");
                            })
                    .register();
        }

        @Tool
        String bravo(Cancellation cancellation, @ToolArg(defaultValue = "1") int price) throws InterruptedException {
            BRAVO_LATCH.countDown();
            int c = 0;
            while (c++ < 20) {
                Result r = cancellation.check();
                if (r.isRequested()
                        && r.reason().isPresent()
                        && r.reason().get().equals("No reason at all")) {
                    CANCELLED.set(true);
                    throw new OperationCancellationException();
                }
                TimeUnit.MILLISECONDS.sleep(500);
            }
            return "OK";
        }

        @Tool
        String charlie(Cancellation cancellation, @ToolArg(defaultValue = "1") int price) {
            CompletableFuture<String> future = new CompletableFuture<>();
            cancellation.onCancelled(reason -> {
                CANCEL_REASON.set(reason);
                future.completeExceptionally(new OperationCancellationException());
            });
            CHARLIE_LATCH.countDown();
            try {
                return future.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof OperationCancellationException oce) {
                    CANCELLED.set(true);
                    throw oce;
                }
                throw new RuntimeException(e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Tool
        String delta(Cancellation cancellation, @ToolArg(defaultValue = "1") int price) {
            CompletableFuture<String> future = new CompletableFuture<>();
            cancellation.onCancelled(reason -> {
                CANCEL_REASON.set(reason);
                CANCEL_ACTION_COUNT.incrementAndGet();
                future.completeExceptionally(new OperationCancellationException());
            });
            cancellation.onCancelled(reason -> CANCEL_ACTION_COUNT.incrementAndGet());
            DELTA_LATCH.countDown();
            try {
                return future.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof OperationCancellationException oce) {
                    CANCELLED.set(true);
                    throw oce;
                }
                throw new RuntimeException(e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Tool
        String echo(Cancellation cancellation, @ToolArg(defaultValue = "1") int price) {
            try {
                cancellation.onCancelled(null);
                return "OK";
            } catch (IllegalArgumentException expected) {
                return "REJECTED";
            }
        }

    }

}
