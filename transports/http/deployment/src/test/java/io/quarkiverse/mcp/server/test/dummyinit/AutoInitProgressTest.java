package io.quarkiverse.mcp.server.test.dummyinit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.sse.client.SseClient.SseEventSubscriber;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.json.JsonObject;

public class AutoInitProgressTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.http.streamable.auto-init", "true");

    @Test
    public void testProgressNotifications() throws Exception {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        URI endpoint = client.mcpEndpoint();
        client.terminateSession();
        client.disconnect();

        CompletableFuture<JsonObject> toolResponse = new CompletableFuture<>();
        List<JsonObject> notifications = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> allNotificationsReceived = new CompletableFuture<>();

        HttpClient.newHttpClient().sendAsync(
                autoInitRequest(endpoint, toolsCallWithProgress("longRunning", 1, "myToken")),
                BodyHandlers.fromLineSubscriber(
                        new SseEventSubscriber(event -> {
                            if ("message".equals(event.name())) {
                                JsonObject json = new JsonObject(event.data());
                                if (json.containsKey("result") || json.containsKey("error")) {
                                    toolResponse.complete(json);
                                } else if (!json.containsKey("id")) {
                                    notifications.add(json);
                                    if (notifications.size() == 3) {
                                        allNotificationsReceived.complete(null);
                                    }
                                }
                            }
                        })));

        allNotificationsReceived.get(10, TimeUnit.SECONDS);
        JsonObject response = toolResponse.get(10, TimeUnit.SECONDS);
        assertFalse(response.getJsonObject("result").getBoolean("isError"));
        assertEquals("ok",
                response.getJsonObject("result").getJsonArray("content").getJsonObject(0).getString("text"));

        assertEquals(3, notifications.size());
        assertProgressNotification(notifications.get(0), "myToken", 1, 3, "Step 1");
        assertProgressNotification(notifications.get(1), "myToken", 2, 3, "Step 2");
        assertProgressNotification(notifications.get(2), "myToken", 3, 3, "Step 3");
    }

    private void assertProgressNotification(JsonObject notification, String token, int progress, int total,
            String message) {
        JsonObject params = notification.getJsonObject("params");
        assertEquals(token, params.getString("progressToken"));
        assertEquals(progress, params.getInteger("progress"));
        assertEquals(total, params.getInteger("total"));
        assertEquals(message, params.getString("message"));
    }

    private static HttpRequest autoInitRequest(URI endpoint, JsonObject body) {
        return HttpRequest.newBuilder()
                .uri(endpoint)
                .version(Version.HTTP_1_1)
                .header("Accept", "text/event-stream")
                .header("Accept", "application/json")
                .POST(BodyPublishers.ofString(body.encode()))
                .build();
    }

    private JsonObject toolsCallWithProgress(String name, int id, String progressToken) {
        return new JsonObject()
                .put("jsonrpc", "2.0")
                .put("method", McpAssured.TOOLS_CALL)
                .put("id", id)
                .put("params", new JsonObject()
                        .put("name", name)
                        .put("_meta", new JsonObject()
                                .put("progressToken", progressToken)));
    }

    public static class MyTools {

        @Tool
        Uni<String> longRunning(Progress progress) {
            if (progress.token().isEmpty()) {
                return Uni.createFrom().item("nok");
            }
            return Uni.createFrom().item(() -> {
                for (int i = 1; i <= 3; i++) {
                    progress.notificationBuilder()
                            .setProgress(i)
                            .setTotal(3)
                            .setMessage("Step " + i)
                            .build()
                            .send()
                            .await()
                            .indefinitely();
                }
                return "ok";
            }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
        }
    }

}
