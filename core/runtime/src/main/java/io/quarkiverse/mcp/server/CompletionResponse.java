package io.quarkiverse.mcp.server;

import java.util.List;

/**
 * Response to a {@code completion/complete} request from the client.
 *
 * @param values the completion values (must not be {@code null})
 * @param total the total number of completion values available
 * @param hasMore {@code true} if there are more additional completion values available
 */
public record CompletionResponse(List<String> values, Integer total, Boolean hasMore) {

    /**
     *
     * @param values
     * @return a new completion response
     */
    public static CompletionResponse create(List<String> values) {
        return new CompletionResponse(values, null, null);
    }

    public CompletionResponse {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
    }

}
