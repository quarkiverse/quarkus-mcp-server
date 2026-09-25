package io.quarkiverse.mcp.server.hibernate.validator.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.Feature;
import io.quarkiverse.mcp.server.hibernate.validator.ConstraintViolationConverter.FeatureContext;

public class FeatureContextTest {

    @Test
    public void testConstructor() {
        assertEquals("feature must not be null",
                assertThrows(IllegalArgumentException.class, () -> new FeatureContext(null, null))
                        .getMessage());
        assertEquals("serverName must not be null",
                assertThrows(IllegalArgumentException.class, () -> new FeatureContext(Feature.PROMPT, null))
                        .getMessage());
    }

    @Test
    @SuppressWarnings({ "deprecation", "removal" })
    public void testAccessors() {
        FeatureContext context = new FeatureContext(Feature.TOOL, "alpha");
        assertEquals(Feature.TOOL, context.feat());
        assertEquals("alpha", context.serverName());
        // The deprecated accessor maps to the legacy enum kept for backward compatibility
        assertEquals(io.quarkiverse.mcp.server.runtime.Feature.TOOL, context.feature());
    }

}
