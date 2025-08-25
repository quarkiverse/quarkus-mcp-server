package io.quarkiverse.mcp.server;

import java.util.List;
import java.util.Map;

/**
 * Response to a {@code resources/read} request from the client.
 *
 * @param contents text and/or binary data (must not be {@code null})
 * @param _meta the optional metadata
 */
public record ResourceResponse(List<ResourceContents> contents, Map<MetaKey, Object> _meta) {

    public ResourceResponse(List<ResourceContents> contents) {
        this(contents, null);
    }

    public ResourceResponse {
        if (contents == null) {
            throw new IllegalArgumentException("contents must not be null");
        }
    }

}
