package io.quarkiverse.mcp.server.runtime;

import java.util.List;

import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ToolResponse;

public interface McpMetadata {

    List<FeatureMetadata<PromptResponse>> prompts();

    List<FeatureMetadata<ToolResponse>> tools();

    List<FeatureMetadata<ResourceResponse>> resources();

}
