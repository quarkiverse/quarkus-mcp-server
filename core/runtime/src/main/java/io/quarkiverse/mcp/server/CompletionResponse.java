package io.quarkiverse.mcp.server;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Response to a {@code completion/complete} request from the client.
 *
 * @param values the completion values (must not be {@code null})
 * @param total the total number of completion values available
 * @param hasMore {@code true} if there are more additional completion values available
 * @param _meta the optional metadata
 */
@JsonInclude(Include.NON_NULL)
public record CompletionResponse(List<String> values, Integer total, Boolean hasMore, Map<MetaKey, Object> _meta) {

    /**
     * @param values
     * @return a new completion response
     */
    public static CompletionResponse create(List<String> values) {
        return new CompletionResponse(values, null, null);
    }

    public CompletionResponse(List<String> values, Integer total, Boolean hasMore) {
        this(values, total, hasMore, null);
    }

    public CompletionResponse {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
    }

}
