package io.quarkiverse.mcp.server.runtime;

import java.util.List;

import jakarta.inject.Singleton;

import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceContentsEncoder;
import io.quarkiverse.mcp.server.ResourceResponse;

@Singleton
public class ResourceContentsEncoderResultMapper
        extends ListEncoderResultMapper<ResourceContents, ResourceContentsEncoder<?>, ResourceResponse> {

    @Override
    protected ResourceResponse toResponse(List<ResourceContents> contents) {
        return new ResourceResponse(contents);
    }

}
