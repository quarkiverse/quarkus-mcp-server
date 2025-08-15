package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.MetaKey;

public class MetaKeyTest {

    @Test
    public void testIsValidName() {
        assertFalse(MetaKey.isValidName(null));
        assertFalse(MetaKey.isValidName(""));
        assertFalse(MetaKey.isValidName("\n"));
        assertTrue(MetaKey.isValidName("foo.bar.baz"));
        assertTrue(MetaKey.isValidName("foo_bar-baz"));
        assertFalse(MetaKey.isValidName("_foo"));
        assertFalse(MetaKey.isValidName("foo-"));
        assertFalse(MetaKey.isValidName("[foo]"));
        assertFalse(MetaKey.isValidName("foo bar"));
    }

    @Test
    public void testIsValidPrefix() {
        assertFalse(MetaKey.isValidPrefix(null));
        assertFalse(MetaKey.isValidPrefix(""));
        assertFalse(MetaKey.isValidPrefix("\n"));
        assertTrue(MetaKey.isValidPrefix("foo/"));
        assertFalse(MetaKey.isValidPrefix("1foo"));
        assertFalse(MetaKey.isValidPrefix("-foo"));
        assertFalse(MetaKey.isValidPrefix(".foo"));
        assertFalse(MetaKey.isValidPrefix("foo bar"));
        assertTrue(MetaKey.isValidPrefix("foo.bar-bar.baz/"));
        assertTrue(MetaKey.isValidPrefix("foo-bar.baz/"));
    }

    @Test
    public void testFrom() {
        MetaKey k = MetaKey.from("foo-bar.baz/name");
        assertEquals("foo-bar.baz/", k.prefix());
        assertEquals("name", k.name());
    }

    @Test
    public void testOf() {
        assertEquals("name", MetaKey.of("name").toString());
        assertEquals("foo.bar/name", new MetaKey("foo.bar/", "name").toString());
        assertEquals("foo.bar/name", MetaKey.of("name", "foo", "bar").toString());
    }

}
