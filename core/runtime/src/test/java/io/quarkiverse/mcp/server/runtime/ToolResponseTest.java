package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.ToolResponse;
import io.vertx.core.json.JsonObject;

public class ToolResponseTest {

    @Test
    public void testConstruct() {
        ToolResponse response = ToolResponse.structuredError("foo");
        assertTrue(response.isError());
        assertEquals("foo", response.structuredContent());
        assertNull(response._meta());
        assertEquals("content must not be null",
                assertThrows(IllegalArgumentException.class, () -> new ToolResponse(false, null, null, null))
                        .getMessage());
        assertThrows(NoSuchElementException.class, () -> new ToolResponse(false, null, "foo", null).firstContent());
        assertThrows(NoSuchElementException.class, () -> new ToolResponse(false, List.of(), "foo", null).firstContent());
    }

    @Test
    public void testStructuredFactoriesContentNotEmptyOnWire() {
        // The factories must never pass null content - JsonObject.mapFrom() drops null members,
        // and CallToolResult requires the content member to be present in every schema revision
        for (ToolResponse response : List.of(ToolResponse.structuredSuccess("foo"), ToolResponse.structuredError("foo"))) {
            assertEquals(List.of(), response.content());
            JsonObject result = JsonObject.mapFrom(response);
            // An empty array must be serialized, the member must not be missing
            assertEquals("[]", result.getJsonArray("content").encode());
        }
    }

}
