package io.quarkiverse.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class McpProtocolVersionTest {

    @Test
    public void testFromKnownVersion() {
        assertSame(McpProtocolVersion.V_2024_11_05, McpProtocolVersion.from("2024-11-05"));
        assertSame(McpProtocolVersion.V_2025_03_26, McpProtocolVersion.from("2025-03-26"));
        assertSame(McpProtocolVersion.V_2025_06_18, McpProtocolVersion.from("2025-06-18"));
        assertSame(McpProtocolVersion.V_2025_11_25, McpProtocolVersion.from("2025-11-25"));
        assertSame(McpProtocolVersion.V_2026_07_28, McpProtocolVersion.from("2026-07-28"));
    }

    @Test
    public void testFromNull() {
        assertNull(McpProtocolVersion.from(null));
    }

    @Test
    public void testFromUnknownVersion() {
        McpProtocolVersion unknown = McpProtocolVersion.from("9999-01-01");
        assertEquals("9999-01-01", unknown.version());
        assertFalse(unknown.isKnown());
        assertTrue(unknown.isStateless());
    }

    @Test
    public void testFromUnknownOldVersion() {
        McpProtocolVersion unknown = McpProtocolVersion.from("2020-01-01");
        assertEquals("2020-01-01", unknown.version());
        assertFalse(unknown.isKnown());
        assertFalse(unknown.isStateless());
    }

    @Test
    public void testIsKnown() {
        assertTrue(McpProtocolVersion.V_2024_11_05.isKnown());
        assertTrue(McpProtocolVersion.V_2026_07_28.isKnown());
        assertFalse(McpProtocolVersion.from("2099-01-01").isKnown());
    }

    @Test
    public void testIsStateless() {
        assertFalse(McpProtocolVersion.V_2024_11_05.isStateless());
        assertFalse(McpProtocolVersion.V_2025_03_26.isStateless());
        assertFalse(McpProtocolVersion.V_2025_11_25.isStateless());
        assertTrue(McpProtocolVersion.V_2026_07_28.isStateless());
        assertSame(McpProtocolVersion.FIRST_STATELESS, McpProtocolVersion.V_2026_07_28);
    }

    @Test
    public void testEqualsAndHashCode() {
        McpProtocolVersion a = McpProtocolVersion.from("2099-01-01");
        McpProtocolVersion b = McpProtocolVersion.from("2099-01-01");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        assertEquals(McpProtocolVersion.V_2024_11_05, McpProtocolVersion.from("2024-11-05"));
    }

    @Test
    public void testToString() {
        assertEquals("2024-11-05", McpProtocolVersion.V_2024_11_05.toString());
        assertEquals("9999-01-01", McpProtocolVersion.from("9999-01-01").toString());
    }

}
