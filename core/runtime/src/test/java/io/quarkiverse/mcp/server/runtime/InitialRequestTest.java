package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.Implementation;
import io.quarkiverse.mcp.server.InitialRequest;

public class InitialRequestTest {

    @Test
    public void testIllegalArguments() {
        Implementation impl = new Implementation("test", "1.0", "");
        assertEquals("implementation must not be null",
                assertThrows(IllegalArgumentException.class, () -> new InitialRequest(null, null, null, null)).getMessage());
        assertEquals("protocolVersion must not be null",
                assertThrows(IllegalArgumentException.class, () -> new InitialRequest(impl, null, null, null)).getMessage());
        assertEquals("clientCapabilities must not be null",
                assertThrows(IllegalArgumentException.class, () -> new InitialRequest(impl, "1.0", null, null)).getMessage());
        assertEquals("transport must not be null",
                assertThrows(IllegalArgumentException.class, () -> new InitialRequest(impl, "1.0", List.of(), null))
                        .getMessage());
    }

}
