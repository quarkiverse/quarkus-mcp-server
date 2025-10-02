package io.quarkiverse.mcp.server.runtime;

import java.util.List;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.InputSchemaGenerator;
import io.quarkiverse.mcp.server.OutputSchemaGenerator;
import io.quarkiverse.mcp.server.ToolManager;

public record FeatureMethodInfo(String name,
        String title,
        String description,
        String uri,
        String mimeType,
        int size,
        List<FeatureArgument> arguments,
        String declaringClassName,
        ToolManager.ToolAnnotations toolAnnotations,
        Content.Annotations resourceAnnotations,
        String serverName,
        Class<?> outputSchemaFrom,
        Class<? extends OutputSchemaGenerator> outputSchemaGenerator,
        Class<? extends InputSchemaGenerator<?>> inputSchemaGenerator) {

    public List<FeatureArgument> serializedArguments() {
        if (arguments == null) {
            return List.of();
        }
        return arguments.stream().filter(FeatureArgument::isParam).toList();
    }

}