package io.quarkiverse.mcp.server;

import java.util.List;

/**
 * Response to a {@code resources/read} request from the client.
 *
 * @param contents text and/or binary data (must not be {@code null})
 */
public record ResourceResponse(List<ResourceContents> contents) {

    public ResourceResponse {
        if (contents == null) {
            throw new IllegalArgumentException("contents must not be null");
        }
    }

}
