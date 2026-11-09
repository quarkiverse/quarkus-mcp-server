package io.quarkiverse.mcp.server.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.tasks.spi.TaskRecord;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class TaskRecordTest {

    @Test
    void testJsonRoundTrip() {
        Instant createdAt = Instant.ofEpochMilli(1_000);
        TaskRecord record = TaskRecord.builder()
                .id("foo")
                .serverName("<default>")
                .toolName("bar")
                .executionNodeId("node-1")
                .version(3)
                .status(TaskStatus.INPUT_REQUIRED)
                .statusMessage("Waiting")
                .createdAt(createdAt)
                .lastUpdatedAt(createdAt.plusMillis(500))
                .ttl(Duration.ofMinutes(1))
                .pollInterval(Duration.ofMillis(100))
                .inputRequests(new JsonObject().put("roots", new JsonObject().put("method", "roots/list")))
                .outstandingInputRequestKeys(List.of("roots"))
                .inputResponses(new JsonObject())
                .build();

        TaskRecord copy = TaskRecord.fromJson(record.toJson());
        assertEquals(record.toJson(), copy.toJson());
        assertEquals("foo", copy.id());
        assertEquals("node-1", copy.executionNodeId());
        assertEquals(3, copy.version());
        assertEquals(TaskStatus.INPUT_REQUIRED, copy.status());
        assertEquals(createdAt, copy.createdAt());
        assertEquals(createdAt.plusMillis(500), copy.lastUpdatedAt());
        assertEquals(Optional.of(Duration.ofMinutes(1)), copy.ttl());
        assertEquals(List.of("roots"), copy.outstandingInputRequestKeys());
        assertTrue(copy.hasInputRequest("roots"));
        assertEquals("roots/list", copy.inputRequest("roots").getString("method"));
        assertNull(copy.result());
        assertNull(copy.cancellationReason());

        assertFalse(copy.isExpired(createdAt.plusSeconds(30)));
        assertTrue(copy.isExpired(createdAt.plusSeconds(61)));

        // Unlimited TTL
        TaskRecord unlimited = TaskRecord.fromJson(record.toBuilder().ttl(null).build().toJson());
        assertEquals(Optional.empty(), unlimited.ttl());
        assertFalse(unlimited.isExpired(Instant.MAX));
    }

    @Test
    void testImmutability() {
        JsonObject result = new JsonObject().put("isError", false);
        TaskRecord record = TaskRecord.builder()
                .id("foo")
                .serverName("<default>")
                .toolName("bar")
                .executionNodeId("node-1")
                .status(TaskStatus.COMPLETED)
                .createdAt(Instant.now())
                .pollInterval(Duration.ofSeconds(1))
                .result(result)
                .build();
        // Modifying the value passed to the builder has no effect
        result.put("isError", true);
        assertFalse(record.result().getBoolean("isError"));
        // Modifying the returned value has no effect
        record.result().put("isError", true);
        assertFalse(record.result().getBoolean("isError"));
    }

    @Test
    void testPojoValuesAreNormalized() {
        // JsonObject#copy() would fail for a POJO value
        JsonObject error = new JsonObject().put("code", -1).put("data", new Detail("foo", 42));
        TaskRecord record = TaskRecord.builder()
                .id("foo")
                .serverName("<default>")
                .toolName("bar")
                .executionNodeId("node-1")
                .status(TaskStatus.FAILED)
                .createdAt(Instant.now())
                .pollInterval(Duration.ofSeconds(1))
                .error(error)
                .build();
        JsonObject data = record.error().getJsonObject("data");
        assertEquals("foo", data.getString("name"));
        assertEquals(42, data.getInteger("value"));
        assertEquals(record.toJson(), TaskRecord.fromJson(record.toJson()).toJson());
    }

    @Test
    void testRequiredValues() {
        TaskRecord.Builder builder = TaskRecord.builder()
                .id("foo")
                .serverName("<default>")
                .toolName("bar")
                .status(TaskStatus.WORKING)
                .createdAt(Instant.now())
                .pollInterval(Duration.ofSeconds(1));
        NullPointerException e = assertThrows(NullPointerException.class, builder::build);
        assertEquals("executionNodeId must not be null", e.getMessage());
        builder.executionNodeId("node-1");
        assertEquals("node-1", builder.build().executionNodeId());

        // A JSON document without the executionNodeId is rejected as well
        JsonObject json = builder.build().toJson();
        json.remove("executionNodeId");
        assertThrows(NullPointerException.class, () -> TaskRecord.fromJson(json));
    }

    @Test
    void testAddInputRequestsAndResponses() {
        TaskRecord record = TaskRecord.builder()
                .id("foo")
                .serverName("<default>")
                .toolName("bar")
                .executionNodeId("node-1")
                .status(TaskStatus.INPUT_REQUIRED)
                .createdAt(Instant.now())
                .pollInterval(Duration.ofSeconds(1))
                .inputRequests(new JsonObject().put("roots", new JsonObject().put("method", "roots/list")))
                .inputResponses(new JsonObject().put("roots", new JsonObject().put("roots", new JsonArray())))
                .build();

        JsonObject addedResponse = new JsonObject().put("detail", new Detail("baz", 1));
        TaskRecord updated = record.toBuilder()
                .addInputRequests(new JsonObject().put("sampling",
                        new JsonObject().put("method", "sampling/createMessage")))
                .addInputResponses(new JsonObject().put("sampling", addedResponse))
                .build();

        assertTrue(updated.hasInputRequest("roots"));
        assertTrue(updated.hasInputRequest("sampling"));
        assertTrue(updated.inputResponses().containsKey("roots"));
        assertEquals("baz", updated.inputResponses().getJsonObject("sampling").getJsonObject("detail").getString("name"));
        // The added value is normalized
        addedResponse.put("extra", true);
        assertFalse(updated.inputResponses().getJsonObject("sampling").containsKey("extra"));
        // The original record is not modified
        assertFalse(record.hasInputRequest("sampling"));
        assertFalse(record.inputResponses().containsKey("sampling"));

        // Adding to empty values
        TaskRecord empty = record.toBuilder()
                .inputResponses(null)
                .addInputResponses(new JsonObject().put("roots", new JsonObject()))
                .build();
        assertEquals(new JsonObject().put("roots", new JsonObject()), empty.inputResponses());
    }

    public record Detail(String name, int value) {
    }

}
