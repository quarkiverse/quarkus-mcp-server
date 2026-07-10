package io.quarkiverse.mcp.server.runtime;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.quarkiverse.mcp.server.CacheControl;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.IconsProvider;
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
        String methodName,
        ToolManager.ToolAnnotations toolAnnotations,
        Content.Annotations resourceAnnotations,
        CacheControl cacheControl,
        Set<String> serverNames,
        Type outputSchemaFrom,
        Class<? extends OutputSchemaGenerator> outputSchemaGenerator,
        Class<? extends InputSchemaGenerator<?>> inputSchemaGenerator,
        // meta key (prefix + name) -> json
        Map<String, String> metadata,
        List<Class<?>> inputGuardrails,
        List<Class<?>> outputGuardrails,
        Class<? extends IconsProvider> iconsProvider) {

    public List<FeatureArgument> serializedArguments() {
        if (arguments == null) {
            return List.of();
        }
        return arguments.stream().filter(FeatureArgument::isParam).toList();
    }

}