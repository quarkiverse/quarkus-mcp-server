package io.quarkiverse.mcp.server.runtime.mcpjava;

import java.util.List;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.PromptResponseEncoder;

@Singleton
@Priority(10)
public class McpJavaPromptMessageEncoder implements PromptResponseEncoder<org.mcpjava.server.prompts.PromptMessage> {

    @Override
    public boolean supports(Class<?> runtimeType) {
        return org.mcpjava.server.prompts.PromptMessage.class.isAssignableFrom(runtimeType);
    }

    @Override
    public PromptResponse encode(org.mcpjava.server.prompts.PromptMessage value) {
        PromptMessage converted = McpJavaTypeConverter.convertPromptMessage(value);
        return new PromptResponse(null, List.of(converted));
    }
}
