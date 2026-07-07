package io.quarkiverse.mcp.server;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Response to a {@code resources/read} request from the client.
 *
 * @param contents text and/or binary data (must not be {@code null})
 * @param _meta the optional metadata
 * @param cacheControl the optional cache control hints; if {@code null} no caching fields are included in the response
 */
public record ResourceResponse(List<ResourceContents> contents, Map<MetaKey, Object> _meta,
        CacheControl cacheControl) {

    public ResourceResponse(ResourceContents contents) {
        this(List.of(contents), null, null);
    }

    public ResourceResponse(List<ResourceContents> contents) {
        this(contents, null, null);
    }

    public ResourceResponse(List<ResourceContents> contents, Map<MetaKey, Object> _meta) {
        this(contents, _meta, null);
    }

    public ResourceResponse {
        if (contents == null) {
            throw new IllegalArgumentException("contents must not be null");
        }
    }

    public ResourceContents firstContents() {
        if (contents == null || contents.isEmpty()) {
            throw new NoSuchElementException();
        }
        return contents.get(0);
    }
}
