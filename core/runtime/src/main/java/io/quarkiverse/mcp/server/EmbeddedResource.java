package io.quarkiverse.mcp.server;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A resource embedded into a prompt or tool call result.
 *
 * @param resource (must not be {@code null})
 * @param _meta the optional metadata
 * @see Prompt
 * @see Tool
 */
@JsonInclude(Include.NON_NULL)
public record EmbeddedResource(ResourceContents resource, Map<MetaKey, Object> _meta) implements Content {

    public EmbeddedResource(ResourceContents resource) {
        this(resource, null);
    }

    public EmbeddedResource {
        if (resource == null) {
            throw new IllegalArgumentException("resource must not be null");
        }
    }

    @Override
    public Type type() {
        return Type.RESOURCE;
    }

    @Override
    public EmbeddedResource asResource() {
        return this;
    }
}
