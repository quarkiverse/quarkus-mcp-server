package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class SubscriptionFilterTest {

    @Test
    public void testParseAllFields() {
        JsonObject notifications = new JsonObject()
                .put("toolsListChanged", true)
                .put("promptsListChanged", true)
                .put("resourcesListChanged", true)
                .put("resourceSubscriptions", new JsonArray().add("file:///a.txt").add("file:///b.txt"));

        SubscriptionFilter filter = SubscriptionFilter.parse(notifications);

        assertTrue(filter.toolsListChanged());
        assertTrue(filter.promptsListChanged());
        assertTrue(filter.resourcesListChanged());
        assertEquals(2, filter.resourceSubscriptions().size());
        assertTrue(filter.resourceSubscriptions().contains("file:///a.txt"));
        assertTrue(filter.resourceSubscriptions().contains("file:///b.txt"));
    }

    @Test
    public void testParsePartialFields() {
        JsonObject notifications = new JsonObject()
                .put("toolsListChanged", true);

        SubscriptionFilter filter = SubscriptionFilter.parse(notifications);

        assertTrue(filter.toolsListChanged());
        assertFalse(filter.promptsListChanged());
        assertFalse(filter.resourcesListChanged());
        assertTrue(filter.resourceSubscriptions().isEmpty());
    }

    @Test
    public void testParseEmpty() {
        SubscriptionFilter filter = SubscriptionFilter.parse(new JsonObject());

        assertFalse(filter.toolsListChanged());
        assertFalse(filter.promptsListChanged());
        assertFalse(filter.resourcesListChanged());
        assertTrue(filter.resourceSubscriptions().isEmpty());
    }

    @Test
    public void testMatchesToolsListChanged() {
        SubscriptionFilter filter = SubscriptionFilter.parse(
                new JsonObject().put("toolsListChanged", true));

        assertTrue(filter.matches("notifications/tools/list_changed", null));
        assertFalse(filter.matches("notifications/prompts/list_changed", null));
        assertFalse(filter.matches("notifications/resources/list_changed", null));
    }

    @Test
    public void testMatchesPromptsListChanged() {
        SubscriptionFilter filter = SubscriptionFilter.parse(
                new JsonObject().put("promptsListChanged", true));

        assertFalse(filter.matches("notifications/tools/list_changed", null));
        assertTrue(filter.matches("notifications/prompts/list_changed", null));
    }

    @Test
    public void testMatchesResourcesListChanged() {
        SubscriptionFilter filter = SubscriptionFilter.parse(
                new JsonObject().put("resourcesListChanged", true));

        assertTrue(filter.matches("notifications/resources/list_changed", null));
        assertFalse(filter.matches("notifications/tools/list_changed", null));
    }

    @Test
    public void testMatchesResourceUpdated() {
        SubscriptionFilter filter = SubscriptionFilter.parse(
                new JsonObject().put("resourceSubscriptions",
                        new JsonArray().add("file:///a.txt")));

        assertTrue(filter.matches("notifications/resources/updated", "file:///a.txt"));
        assertFalse(filter.matches("notifications/resources/updated", "file:///b.txt"));
        assertFalse(filter.matches("notifications/resources/updated", null));
    }

    @Test
    public void testMatchesUnknownMethod() {
        SubscriptionFilter filter = SubscriptionFilter.parse(
                new JsonObject().put("toolsListChanged", true));

        assertFalse(filter.matches("notifications/unknown", null));
        assertFalse(filter.matches(null, null));
    }

    @Test
    public void testNoMatchWhenNotSubscribed() {
        SubscriptionFilter filter = SubscriptionFilter.parse(new JsonObject());

        assertFalse(filter.matches("notifications/tools/list_changed", null));
        assertFalse(filter.matches("notifications/prompts/list_changed", null));
        assertFalse(filter.matches("notifications/resources/list_changed", null));
        assertFalse(filter.matches("notifications/resources/updated", "file:///a.txt"));
    }

    @Test
    public void testToAcknowledgedJson() {
        JsonObject input = new JsonObject()
                .put("toolsListChanged", true)
                .put("resourcesListChanged", true)
                .put("resourceSubscriptions", new JsonArray().add("file:///a.txt"));

        SubscriptionFilter filter = SubscriptionFilter.parse(input);
        JsonObject ack = filter.toAcknowledgedJson();

        assertNotNull(ack);
        assertTrue(ack.getBoolean("toolsListChanged"));
        assertFalse(ack.containsKey("promptsListChanged"));
        assertTrue(ack.getBoolean("resourcesListChanged"));
        JsonArray subs = ack.getJsonArray("resourceSubscriptions");
        assertNotNull(subs);
        assertEquals(1, subs.size());
        assertEquals("file:///a.txt", subs.getString(0));
    }

    @Test
    public void testToAcknowledgedJsonEmpty() {
        SubscriptionFilter filter = SubscriptionFilter.parse(new JsonObject());
        JsonObject ack = filter.toAcknowledgedJson();

        assertNotNull(ack);
        assertTrue(ack.isEmpty());
    }

}
