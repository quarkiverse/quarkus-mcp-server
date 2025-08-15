package io.quarkiverse.mcp.server;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A resource link.
 *
 * @param uri (must not be {@code null})
 * @param mimeType
 * @param description
 * @param _meta the optional metadata
 * @see Prompt
 * @see Tool
 */
@JsonInclude(Include.NON_NULL)
public record ResourceLink(String uri, String mimeType, String name, String title, String description, Integer size,
        Map<MetaKey, Object> _meta) implements Content {

    public ResourceLink(String uri, String name) {
        this(uri, null, name, null, null, null, null);
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
