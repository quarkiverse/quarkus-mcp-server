package io.quarkiverse.mcp.server.runtime.mcpjava;

import java.util.List;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.PromptResponseEncoder;

@Singleton
@Priority(10)
public class McpJavaPromptResponseEncoder implements PromptResponseEncoder<org.mcpjava.server.prompts.PromptResponse> {

    @Override
    public boolean supports(Class<?> runtimeType) {
        return org.mcpjava.server.prompts.PromptResponse.class.isAssignableFrom(runtimeType);
    }

    @Override
    public PromptResponse encode(org.mcpjava.server.prompts.PromptResponse value) {
        List<PromptMessage> messages = value.messages().stream()
                .map(McpJavaTypeConverter::convertPromptMessage)
                .toList();
        return new PromptResponse(value.description().orElse(null), messages,
                McpJavaTypeConverter.convertMetadata(value.metadata()));
    }
}
