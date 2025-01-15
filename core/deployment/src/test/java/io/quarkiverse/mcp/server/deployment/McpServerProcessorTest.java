package io.quarkiverse.mcp.server.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jboss.jandex.ClassType;
import org.jboss.jandex.ParameterizedType;
import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.runtime.FeatureMetadata.Feature;

public class McpServerProcessorTest {

    @Test
    public void testCreateMapperField() {
        assertEquals("RESOURCE_CONTENT",
                McpServerProcessor.createMapperField(Feature.RESOURCE, ClassType.create(DotNames.TEXT_RESOURCE_CONTENTS),
                        DotNames.RESOURCE_RESPONSE,
                        c -> "CONTENT"));
        assertEquals("IDENTITY",
                McpServerProcessor.createMapperField(Feature.RESOURCE,
                        ParameterizedType.create(DotNames.UNI, ClassType.create(DotNames.RESOURCE_RESPONSE)),
                        DotNames.RESOURCE_RESPONSE,
                        c -> "CONTENT"));
    }

}
