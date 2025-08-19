package io.quarkiverse.mcp.server;

/**
 * A response to a {@link SamplingRequest}.
 *
 * @param content (must not be {@code null})
 * @param model The name of the model that generated the message (must not be {@code null})
 * @param role (must not be {@code null})
 * @param stopReason The reason why sampling stopped, if known
 * @param meta (must not be {@code null})
 */
public record SamplingResponse(Content content, String model, Role role, String stopReason, Meta meta) {

    public SamplingResponse {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (meta == null) {
            throw new IllegalArgumentException("meta must not be null");
        }
    }
}
