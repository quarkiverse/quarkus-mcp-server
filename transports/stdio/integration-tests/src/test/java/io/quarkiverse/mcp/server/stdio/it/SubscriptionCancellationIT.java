package io.quarkiverse.mcp.server.stdio.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStdioTestClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class SubscriptionCancellationIT {

    @Test
    public void testCancelSubscriptionViaNotification() {
        try (McpStdioTestClient client = McpAssured.newStdioClient()
                .setStateless()
                .build()
                .connect()) {

            String uri = "file:///project/alpha";

            // Subscribe to resource updates
            JsonObject listenRequest = client.newRequest("subscriptions/listen");
            listenRequest.put("params", new JsonObject()
                    .put("notifications", new JsonObject()
                            .put("resourceSubscriptions", new JsonArray().add(uri))));
            McpAssured.injectStatelessMeta(listenRequest);
            client.sendAndForget(listenRequest);

            // Wait for acknowledged
            client.waitForNotifications(1);

            // Trigger resource update via tool call and verify notification arrives
            client.when()
                    .toolsCall("triggerAlphaUpdate", Map.of(), r -> {
                        assertFalse(r.isError());
                        assertEquals("OK", r.firstContent().asText().text());
                    })
                    .thenAssertResults();

            List<JsonObject> notifications = client.waitForNotifications(2).notifications();
            assertEquals("notifications/resources/updated", notifications.get(1).getString("method"));
            assertEquals(uri, notifications.get(1).getJsonObject("params").getString("uri"));

            // Cancel the subscription via notifications/cancelled
            JsonObject cancelNotification = client.newMessage("notifications/cancelled")
                    .put("params", new JsonObject()
                            .put("requestId", listenRequest.getValue("id")));
            McpAssured.injectStatelessMeta(cancelNotification);
            client.sendAndForget(cancelNotification);

            // Give the server time to process the cancellation
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Trigger another resource update — should NOT produce a notification
            client.when()
                    .toolsCall("triggerAlphaUpdate", Map.of(), r -> {
                        assertFalse(r.isError());
                    })
                    .thenAssertResults();

            // Wait and verify no new notification arrives
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            assertEquals(2, client.snapshot().notifications().size(),
                    "No new notification should arrive after cancelling the subscription");
        }
    }
}
