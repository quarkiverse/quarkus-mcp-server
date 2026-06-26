package io.quarkiverse.mcp.server.runtime.mcpjava;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.ToolResponseEncoder;

@Singleton
@Priority(10)
public class McpJavaToolResponseEncoder implements ToolResponseEncoder<org.mcpjava.server.tools.ToolResponse> {

    @Override
    public boolean supports(Class<?> runtimeType) {
        return org.mcpjava.server.tools.ToolResponse.class.isAssignableFrom(runtimeType);
    }

    @Override
    public ToolResponse encode(org.mcpjava.server.tools.ToolResponse value) {
        return new ToolResponse(value.isError(),
                McpJavaTypeConverter.convertContentBlocks(value.content()),
                value.structuredContent().orElse(null),
                McpJavaTypeConverter.convertMetadata(value.metadata()));
    }
}
