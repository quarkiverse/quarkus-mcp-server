package io.quarkiverse.mcp.server.runtime;

import java.util.List;

import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ToolResponse;

public interface McpMetadata {

    List<FeatureMetadata<PromptResponse>> prompts();

    boolean isPromptManagerUsed();

    List<FeatureMetadata<CompletionResponse>> promptCompletions();

    List<FeatureMetadata<ToolResponse>> tools();

    boolean isToolManagerUsed();

    List<FeatureMetadata<ResourceResponse>> resources();

    List<FeatureMetadata<ResourceResponse>> resourceTemplates();

    boolean isResourceManagerUsed();

    boolean isResourceTemplateManagerUsed();

    List<FeatureMetadata<CompletionResponse>> resourceTemplateCompletions();

}
