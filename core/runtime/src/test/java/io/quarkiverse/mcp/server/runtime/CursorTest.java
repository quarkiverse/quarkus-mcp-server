package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    public void testIsInitial() {
        assertTrue(new Cursor(Instant.EPOCH, Instant.now()).isInitial());
        assertFalse(new Cursor(Instant.parse("2025-02-17T17:07:26.138163913Z"), Instant.now()).isInitial());
    }

}
