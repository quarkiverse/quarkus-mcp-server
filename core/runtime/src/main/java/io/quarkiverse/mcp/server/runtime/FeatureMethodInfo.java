package io.quarkiverse.mcp.server.runtime;

import java.util.List;

import io.quarkiverse.mcp.server.ToolManager;

public record FeatureMethodInfo(String name,
        String description,
        String uri,
        String mimeType,
        List<FeatureArgument> arguments,
        String declaringClassName,
        ToolManager.ToolAnnotations toolAnnotations,
        String serverName) {

    public List<FeatureArgument> serializedArguments() {
        if (arguments == null) {
            return List.of();
        }
        return arguments.stream().filter(FeatureArgument::isParam).toList();
    }

}