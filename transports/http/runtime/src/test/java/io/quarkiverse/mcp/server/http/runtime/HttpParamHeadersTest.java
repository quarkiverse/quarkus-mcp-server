package io.quarkiverse.mcp.server.http.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class HttpParamHeadersTest {

    @Test
    public void testIsValidHttpToken() {
        assertTrue(HttpParamHeaders.isValidHttpToken("Region"));
        assertTrue(HttpParamHeaders.isValidHttpToken("Content-Type"));
        assertTrue(HttpParamHeaders.isValidHttpToken("X-Custom"));
        assertTrue(HttpParamHeaders.isValidHttpToken("abc123"));
        assertTrue(HttpParamHeaders.isValidHttpToken("a"));
        // tchar specials
        assertTrue(HttpParamHeaders.isValidHttpToken("!#$%&'*+-.^_`|~"));
        // invalid chars
        assertFalse(HttpParamHeaders.isValidHttpToken("has space"));
        assertFalse(HttpParamHeaders.isValidHttpToken("has\ttab"));
        assertFalse(HttpParamHeaders.isValidHttpToken("with/slash"));
        assertFalse(HttpParamHeaders.isValidHttpToken("with(paren"));
        assertFalse(HttpParamHeaders.isValidHttpToken("with@at"));
        assertFalse(HttpParamHeaders.isValidHttpToken("with=equals"));
        assertFalse(HttpParamHeaders.isValidHttpToken("with\"quote"));
        assertFalse(HttpParamHeaders.isValidHttpToken("with[bracket"));
        assertFalse(HttpParamHeaders.isValidHttpToken("with{brace"));
    }
}
