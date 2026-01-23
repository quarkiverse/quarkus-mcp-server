package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.ProgressToken;

public class ProgressTokenTest {

    @Test
    public void testConstruct() {
        assertEquals("Token must be a string or a number",
                assertThrows(IllegalArgumentException.class, () -> new ProgressToken(null))
                        .getMessage());
        ProgressToken token1 = new ProgressToken("foo");
        assertEquals(ProgressToken.Type.STRING, token1.type());
        assertEquals("foo", token1.asString());
        assertEquals("Token is not a number",
                assertThrows(IllegalArgumentException.class, () -> token1.asInteger())
                        .getMessage());
        ProgressToken token2 = new ProgressToken(10);
        assertEquals(ProgressToken.Type.INTEGER, token2.type());
        assertEquals("10", token2.asString());
        assertEquals(10, token2.asInteger());
    }

}
