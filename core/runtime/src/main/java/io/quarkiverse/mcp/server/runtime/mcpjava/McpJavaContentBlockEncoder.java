package io.quarkiverse.mcp.server.runtime.mcpjava;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

import org.mcpjava.server.content.ContentBlock;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.ContentEncoder;

@Singleton
@Priority(10)
public class McpJavaContentBlockEncoder implements ContentEncoder<ContentBlock> {

    @Override
    public boolean supports(Class<?> runtimeType) {
        return ContentBlock.class.isAssignableFrom(runtimeType);
    }

    @Override
    public Content encode(ContentBlock value) {
        return McpJavaTypeConverter.convertContentBlock(value);
    }
}
