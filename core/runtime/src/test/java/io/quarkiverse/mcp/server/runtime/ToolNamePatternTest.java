package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.Tool;

public class ToolNamePatternTest {

    static final Pattern SPEC_PATTERN = Pattern.compile(Tool.SPEC_NAME_PATTERN);

    @Test
    public void testSpecExamples() {
        // Example valid tool names from the spec
        assertTrue(SPEC_PATTERN.matcher("getUser").matches());
        assertTrue(SPEC_PATTERN.matcher("DATA_EXPORT_v2").matches());
        assertTrue(SPEC_PATTERN.matcher("admin.tools.list").matches());
    }

    @Test
    public void testValidCharacters() {
        // Lowercase letters
        assertTrue(SPEC_PATTERN.matcher("abc").matches());
        // Uppercase letters
        assertTrue(SPEC_PATTERN.matcher("ABC").matches());
        // Digits
        assertTrue(SPEC_PATTERN.matcher("123").matches());
        // Underscore
        assertTrue(SPEC_PATTERN.matcher("foo_bar").matches());
        // Hyphen
        assertTrue(SPEC_PATTERN.matcher("alpha-tool1").matches());
        // Dot
        assertTrue(SPEC_PATTERN.matcher("admin.tools.list").matches());
        // Mixed
        assertTrue(SPEC_PATTERN.matcher("My_Tool-v2.1").matches());
        // Single character (minimum length)
        assertTrue(SPEC_PATTERN.matcher("x").matches());
    }

    @Test
    public void testLength() {
        // Exactly 128 characters (maximum)
        assertTrue(SPEC_PATTERN.matcher("x".repeat(128)).matches());
        // 129 characters (exceeds maximum)
        assertFalse(SPEC_PATTERN.matcher("x".repeat(129)).matches());
        // Empty string (below minimum)
        assertFalse(SPEC_PATTERN.matcher("").matches());
    }

    @Test
    public void testInvalidCharacters() {
        // Spaces
        assertFalse(SPEC_PATTERN.matcher("my tool").matches());
        // Commas
        assertFalse(SPEC_PATTERN.matcher("tool,name").matches());
        // Special characters
        assertFalse(SPEC_PATTERN.matcher("tool@name").matches());
        assertFalse(SPEC_PATTERN.matcher("tool#name").matches());
        assertFalse(SPEC_PATTERN.matcher("tool$name").matches());
        assertFalse(SPEC_PATTERN.matcher("tool%name").matches());
        assertFalse(SPEC_PATTERN.matcher("tool&name").matches());
        assertFalse(SPEC_PATTERN.matcher("tool!name").matches());
        // Slash
        assertFalse(SPEC_PATTERN.matcher("tool/name").matches());
        // Colon
        assertFalse(SPEC_PATTERN.matcher("tool:name").matches());
        // Unicode
        assertFalse(SPEC_PATTERN.matcher("tööl").matches());
    }

}
