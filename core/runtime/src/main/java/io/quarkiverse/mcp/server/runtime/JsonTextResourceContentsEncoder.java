package io.quarkiverse.mcp.server.runtime;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceContentsEncoder;
import io.quarkiverse.mcp.server.TextResourceContents;

@Singleton
@Priority(0)
public class JsonTextResourceContentsEncoder implements ResourceContentsEncoder<Object> {

    @Inject
    ObjectMapper mapper;

    @Override
    public boolean supports(Class<?> runtimeType) {
        return true;
    }

    @Override
    public ResourceContents encode(ResourceContentsData<Object> data) {
        try {
            return TextResourceContents.create(data.uri().value(), mapper.writeValueAsString(data.data()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

}
