package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.RequestId;

public class RequestIdTest {

    @Test
    public void testIllegalArguments() {
        assertEquals("value must not be null",
                assertThrows(IllegalArgumentException.class, () -> new RequestId(null)).getMessage());
        assertEquals("value must be string or number",
                assertThrows(IllegalArgumentException.class, () -> new RequestId(LocalDateTime.now())).getMessage());
        assertEquals("Request id is not a number",
                assertThrows(IllegalArgumentException.class, () -> new RequestId("foo").asInteger()).getMessage());
        assertEquals(1, new RequestId(1.0).asInteger());
        assertEquals("1", new RequestId(1).asString());
    }

}
