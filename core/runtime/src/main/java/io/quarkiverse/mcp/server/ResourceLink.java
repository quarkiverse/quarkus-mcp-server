package io.quarkiverse.mcp.server;

import java.util.Map;

/**
 * A resource link.
 *
 * @param uri (must not be {@code null})
 * @param mimeType (may be {@code null})
 * @param name (must not be {@code null})
 * @param title (may be {@code null})
 * @param description (may be {@code null})
 * @param _meta the optional metadata (may be {@code null})
 * @param annotations the optional annotations (may be {@code null})
 * @see Prompt
 * @see Tool
 */
public record ResourceLink(String uri, String mimeType, String name, String title, String description, Integer size,
        Map<MetaKey, Object> _meta, Annotations annotations) implements Content {

    public ResourceLink(String uri, String name) {
        this(uri, null, name, null, null, null, null, null);
    }

    public ResourceLink {
        if (uri == null) {
            throw new IllegalArgumentException("uri must not be null");
        }
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
    }

    @Override
    public ResourceLink asResourceLink() {
        return this;
    }

    @Override
    public Type type() {
        return Type.RESOURCE_LINK;
    }

}
