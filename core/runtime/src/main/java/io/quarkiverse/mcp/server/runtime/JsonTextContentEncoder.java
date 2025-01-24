package io.quarkiverse.mcp.server.runtime;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.ContentEncoder;
import io.quarkiverse.mcp.server.TextContent;

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
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

}
