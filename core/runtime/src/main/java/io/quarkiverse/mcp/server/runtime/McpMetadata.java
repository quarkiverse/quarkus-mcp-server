package io.quarkiverse.mcp.server.runtime;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.DefaultValueConverter;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ToolResponse;

public interface McpMetadata {

    Set<String> serverNames();

    List<FeatureMetadata<PromptResponse>> prompts();

    List<FeatureMetadata<CompletionResponse>> promptCompletions();

    List<FeatureMetadata<ToolResponse>> tools();

    List<FeatureMetadata<ResourceResponse>> resources();

    List<FeatureMetadata<ResourceResponse>> resourceTemplates();

    List<FeatureMetadata<CompletionResponse>> resourceTemplateCompletions();

    List<FeatureMetadata<Void>> notifications();

    boolean isPromptManagerUsed();

    boolean isToolManagerUsed();

    boolean isResourceManagerUsed();

    boolean isResourceTemplateManagerUsed();

    Map<Type, DefaultValueConverter<?>> defaultValueConverters();

    static <T> FeatureMetadata<T> findFeatureByName(List<FeatureMetadata<T>> features, String name) {
        return features.stream().filter(fm -> fm.info().name().equals(name)).findFirst().orElse(null);
    }

}
