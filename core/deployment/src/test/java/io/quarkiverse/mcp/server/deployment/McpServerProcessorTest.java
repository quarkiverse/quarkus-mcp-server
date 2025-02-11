package io.quarkiverse.mcp.server.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jboss.jandex.ClassType;
import org.jboss.jandex.ParameterizedType;
import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.runtime.Feature;

public class McpServerProcessorTest {

    @Test
    public void testCreateMapperField() {
        assertEquals("ResourceContent",
                McpServerProcessor.createMapperClassSimpleName(Feature.RESOURCE,
                        ClassType.create(DotNames.TEXT_RESOURCE_CONTENTS),
                        DotNames.RESOURCE_RESPONSE,
                        c -> "Content"));
        assertEquals("Identity",
                McpServerProcessor.createMapperClassSimpleName(Feature.RESOURCE,
                        ParameterizedType.create(DotNames.UNI, ClassType.create(DotNames.RESOURCE_RESPONSE)),
                        DotNames.RESOURCE_RESPONSE,
                        c -> "Content"));
    }

}
