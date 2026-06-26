package io.quarkiverse.mcp.server.runtime.mcpjava;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceContentsEncoder;

@Singleton
@Priority(10)
public class McpJavaResourceContentsEncoder
        implements ResourceContentsEncoder<org.mcpjava.server.resources.ResourceContents> {

    @Override
    public boolean supports(Class<?> runtimeType) {
        return org.mcpjava.server.resources.ResourceContents.class.isAssignableFrom(runtimeType);
    }

    @Override
    public ResourceContents encode(ResourceContentsData<org.mcpjava.server.resources.ResourceContents> value) {
        return McpJavaTypeConverter.convertResourceContents(value.data());
    }
}
