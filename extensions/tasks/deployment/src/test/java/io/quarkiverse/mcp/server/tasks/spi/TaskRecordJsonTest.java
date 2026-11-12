package io.quarkiverse.mcp.server.tasks.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.tasks.TaskStatus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class TaskRecordJsonTest {

    @Test
    void testNull() {
        assertNull(TaskRecordJson.normalize(null));
    }

    @Test
    void testJsonTypesAreDeepCopied() {
        JsonObject nested = new JsonObject().put("name", "foo");
        JsonArray array = new JsonArray().add(1).add(new JsonObject().put("flag", true));
        JsonObject json = new JsonObject()
                .put("string", "bar")
                .put("int", 1)
                .put("long", Long.MAX_VALUE)
                .put("double", 1.5)
                .put("null", null)
                .put("nested", nested)
                .put("array", array);

        JsonObject normalized = TaskRecordJson.normalize(json);
        assertEquals(json, normalized);
        assertNotSame(json, normalized);
        assertEquals(Long.MAX_VALUE, normalized.getLong("long"));

        // The copy is not affected by the modifications of the original value
        nested.put("name", "baz");
        array.getJsonObject(1).put("flag", false);
        assertEquals("foo", normalized.getJsonObject("nested").getString("name"));
        assertEquals(true, normalized.getJsonArray("array").getJsonObject(1).getBoolean("flag"));
    }

    @Test
    void testMapsAndListsAreConverted() {
        JsonObject json = new JsonObject()
                .put("map", Map.of("name", "foo"))
                .put("list", List.of("a", Map.of("b", 1)));

        JsonObject normalized = TaskRecordJson.normalize(json);
        assertInstanceOf(JsonObject.class, normalized.getValue("map"));
        assertInstanceOf(JsonArray.class, normalized.getValue("list"));
        assertEquals("foo", normalized.getJsonObject("map").getString("name"));
        assertEquals(1, normalized.getJsonArray("list").getJsonObject(1).getInteger("b"));
    }

    @Test
    void testNonJsonTypesAreEncodedAndDecoded() {
        Instant now = Instant.now();
        ObjectNode node = new ObjectMapper().createObjectNode().put("type", "object");
        JsonObject json = new JsonObject()
                .put("pojo", new Detail("foo", 42))
                .put("instant", now)
                .put("node", node)
                .put("nested", new JsonObject().put("list", List.of(new Detail("bar", 1))));

        JsonObject normalized = TaskRecordJson.normalize(json);
        assertEquals(new JsonObject().put("name", "foo").put("value", 42), normalized.getJsonObject("pojo"));
        assertInstanceOf(String.class, normalized.getValue("instant"));
        assertEquals(now, normalized.getInstant("instant"));
        assertEquals("object", normalized.getJsonObject("node").getString("type"));
        assertEquals("bar", normalized.getJsonObject("nested").getJsonArray("list").getJsonObject(0).getString("name"));
    }

    @Test
    void testMerge() {
        JsonObject current = new JsonObject().put("a", 1);
        JsonObject merged = TaskRecordJson.merge(current, new JsonObject().put("b", new Detail("foo", 1)));
        assertEquals(new JsonObject().put("a", 1).put("b", new JsonObject().put("name", "foo").put("value", 1)), merged);
        // The current value is never modified
        assertEquals(new JsonObject().put("a", 1), current);
        // Null/empty values
        assertNull(TaskRecordJson.merge(null, null));
        assertSame(current, TaskRecordJson.merge(current, null));
        assertSame(current, TaskRecordJson.merge(current, new JsonObject()));
        assertEquals(new JsonObject().put("b", 2), TaskRecordJson.merge(new JsonObject(), new JsonObject().put("b", 2)));
    }

    @Test
    void testFromJsonMissingValues() {
        Instant createdAt = Instant.ofEpochMilli(1_000);
        JsonObject json = TaskRecord.builder()
                .id("1")
                .serverName("mcp")
                .toolName("foo")
                .executionNodeId("node")
                .version(1)
                .status(TaskStatus.WORKING)
                .createdAt(createdAt)
                .lastUpdatedAt(Instant.ofEpochMilli(2_000))
                .pollInterval(Duration.ofSeconds(1))
                .build()
                .toJson();

        // lastUpdatedAt is optional - defaults to createdAt
        JsonObject withoutLastUpdatedAt = json.copy();
        withoutLastUpdatedAt.remove("lastUpdatedAt");
        assertEquals(createdAt, TaskRecord.fromJson(withoutLastUpdatedAt).lastUpdatedAt());

        // Missing mandatory values are reported with a meaningful message
        Map<String, String> expectedMessages = Map.of(
                "createdAt", "createdAt must not be null",
                "pollIntervalMs", "pollInterval must not be null");
        for (Map.Entry<String, String> e : expectedMessages.entrySet()) {
            JsonObject invalid = json.copy();
            invalid.remove(e.getKey());
            NullPointerException npe = assertThrows(NullPointerException.class, () -> TaskRecord.fromJson(invalid));
            assertEquals(e.getValue(), npe.getMessage());
        }
    }

    public record Detail(String name, int value) {
    }

}
