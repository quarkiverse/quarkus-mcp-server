package io.quarkiverse.mcp.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.McpLog.LogLevel;

public class LogLevelTest {

    @Test
    public void testIsAtLeast() {
        assertTrue(LogLevel.DEBUG.isAtLeast(LogLevel.DEBUG));
        assertFalse(LogLevel.DEBUG.isAtLeast(LogLevel.INFO));
        assertFalse(LogLevel.DEBUG.isAtLeast(LogLevel.EMERGENCY));

        assertTrue(LogLevel.INFO.isAtLeast(LogLevel.DEBUG));
        assertTrue(LogLevel.INFO.isAtLeast(LogLevel.INFO));
        assertFalse(LogLevel.INFO.isAtLeast(LogLevel.WARNING));

        assertTrue(LogLevel.EMERGENCY.isAtLeast(LogLevel.DEBUG));
        assertTrue(LogLevel.EMERGENCY.isAtLeast(LogLevel.EMERGENCY));
    }

}
