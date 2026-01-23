package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.ModelPreferences;

public class ModelPreferencesTest {

    @Test
    public void testIllegalArguments() {
        assertEquals("costPriority must be between 0 and 1",
                assertThrows(IllegalArgumentException.class, () -> new ModelPreferences(new BigDecimal(5), null, null, null))
                        .getMessage());
        assertEquals("intelligencePriority must be between 0 and 1",
                assertThrows(IllegalArgumentException.class,
                        () -> new ModelPreferences(BigDecimal.ONE, null, new BigDecimal(2), null)).getMessage());
        assertEquals("speedPriority must be between 0 and 1",
                assertThrows(IllegalArgumentException.class,
                        () -> new ModelPreferences(BigDecimal.ONE, null, BigDecimal.ONE, new BigDecimal(-5))).getMessage());
    }

}
