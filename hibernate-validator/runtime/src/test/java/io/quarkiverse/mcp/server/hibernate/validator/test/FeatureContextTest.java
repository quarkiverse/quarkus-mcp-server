package io.quarkiverse.mcp.server.hibernate.validator.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.hibernate.validator.ConstraintViolationConverter.FeatureContext;
import io.quarkiverse.mcp.server.runtime.Feature;

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

}
