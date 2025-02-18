package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

public class CursorTest {

    @Test
    public void testEncode() {
        assertEquals("MjAyNS0wMi0xN1QxNzowNzoyNi4xMzgxNjM5MTNaJCQkNg==",
                Cursor.encode(Instant.parse("2025-02-17T17:07:26.138163913Z"), "6"));
    }

    @Test
    public void testDecode() {
        Cursor cursor = Cursor.decode("MjAyNS0wMi0xN1QxNzowNzoyNi4xMzgxNjM5MTNaJCQkNg==");
        assertEquals("6", cursor.name());
        assertEquals(Instant.parse("2025-02-17T17:07:26.138163913Z"), cursor.createdAt());
    }

}
