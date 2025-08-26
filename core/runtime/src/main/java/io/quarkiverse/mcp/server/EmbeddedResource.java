package io.quarkiverse.mcp.server;

import java.util.Map;

/**
 * A resource embedded into a prompt or tool call result.
 *
 * @param resource (must not be {@code null})
 * @param _meta the optional metadata (may be {@code null})
 * @param annotations the optional annotations (may be {@code null})
 * @see Prompt
 * @see Tool
 */
public record EmbeddedResource(ResourceContents resource, Map<MetaKey, Object> _meta, Annotations annotations)
        implements
            Content {

    public EmbeddedResource(ResourceContents resource) {
        this(resource, null, null);
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
