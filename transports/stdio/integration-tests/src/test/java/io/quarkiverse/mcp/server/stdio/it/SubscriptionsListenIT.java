package io.quarkiverse.mcp.server.stdio.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStdioTestClient;
import io.vertx.core.json.JsonObject;

public class SubscriptionsListenIT {

    @Test
    public void testToolsListChanged() {
        try (McpStdioTestClient client = McpAssured.newStdioClient()
                .setStateless()
                .build()
                .connect()) {

            // Send subscriptions/listen with toolsListChanged filter
            JsonObject listenRequest = client.newRequest("subscriptions/listen");
            listenRequest.put("params", new JsonObject()
                    .put("notifications", new JsonObject().put("toolsListChanged", true)));
            McpAssured.injectStatelessMeta(listenRequest);
            client.sendAndForget(listenRequest);

            // Wait for the acknowledged notification
            List<JsonObject> notifications = client.waitForNotifications(1).notifications();
            JsonObject ack = notifications.get(0);
            assertEquals("notifications/subscriptions/acknowledged", ack.getString("method"));
            JsonObject ackParams = ack.getJsonObject("params");
            assertNotNull(ackParams);
            JsonObject meta = ackParams.getJsonObject("_meta");
            assertNotNull(meta);
            assertNotNull(meta.getValue("io.modelcontextprotocol/subscriptionId"));

            // Call toLowerCase — its handler registers a new tool, triggering list_changed
            client.when()
                    .toolsCall("toLowerCase", Map.of("value", "HELLO"), r -> {
                        assertFalse(r.isError());
                        assertEquals("hello", r.firstContent().asText().text());
                    })
                    .thenAssertResults();

            // Wait for the tools/list_changed notification
            notifications = client.waitForNotifications(2).notifications();
            JsonObject listChanged = notifications.get(1);
            assertEquals("notifications/tools/list_changed", listChanged.getString("method"));
            // Verify subscriptionId is injected
            JsonObject listChangedMeta = listChanged.getJsonObject("params").getJsonObject("_meta");
            assertNotNull(listChangedMeta);
            assertEquals(listenRequest.getValue("id"),
                    listChangedMeta.getValue("io.modelcontextprotocol/subscriptionId"));
        }
    }

}
