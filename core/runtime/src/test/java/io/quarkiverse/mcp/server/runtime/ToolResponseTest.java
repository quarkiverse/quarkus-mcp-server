package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.ToolResponse;

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

}
