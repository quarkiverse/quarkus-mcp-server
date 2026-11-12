package io.quarkiverse.mcp.server.runtime;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.ContentEncoder;
import io.quarkiverse.mcp.server.TextContent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Singleton
@Priority(0)
public class JsonTextContentEncoder implements ContentEncoder<Object> {

    @Inject
    ObjectMapper mapper;

    @Override
    public boolean supports(Class<?> runtimeType) {
        return true;
    }

    @Override
    public Content encode(Object value) {
        try {
            return new TextContent(mapper.writeValueAsString(value));
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }

}
