package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

public class CursorTest {

    @Test
    public void testEncode() {
        assertEquals("AAAAAGezbM4IPDbJAAAAAGezbQoIPDbJ",
                Cursor.encode(Instant.parse("2025-02-17T17:07:26.138163913Z"),
                        Instant.parse("2025-02-17T17:08:26.138163913Z")));
    }

    @Test
    public void testDecode() {
        Cursor cursor = Cursor.decode("AAAAAGezbM4IPDbJAAAAAGezbQoIPDbJ");
        assertEquals(Instant.parse("2025-02-17T17:08:26.138163913Z"), cursor.snapshotTimestamp());
        assertEquals(Instant.parse("2025-02-17T17:07:26.138163913Z"), cursor.createdAt());
    }

}
