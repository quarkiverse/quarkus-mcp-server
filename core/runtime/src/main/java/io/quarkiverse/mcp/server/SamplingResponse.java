package io.quarkiverse.mcp.server;

import java.util.List;

/**
 * A response to a {@link SamplingRequest}.
 *
 * @param model The name of the model that generated the message (must not be {@code null})
 * @param role (must not be {@code null})
 * @param stopReason The reason why sampling stopped, if known
 * @param meta (must not be {@code null})
 */
public final class SamplingResponse {

    private final List<Content> contents;
    private final String model;
    private final Role role;
    private final String stopReason;
    private final Meta meta;

    public SamplingResponse(Content content, String model, Role role, String stopReason, Meta meta) {
        this(List.of(content), model, role, stopReason, meta);
    }

    public SamplingResponse(List<? extends Content> content, String model, Role role, String stopReason, Meta meta) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (content.isEmpty()) {
            throw new IllegalArgumentException("content must not be empty");
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
        this.contents = List.copyOf(content);
        this.model = model;
        this.role = role;
        this.stopReason = stopReason;
        this.meta = meta;
    }

    /**
     * Returns the first content block.
     *
     * @throws IllegalStateException if this response contains multiple content blocks
     */
    public Content content() {
        if (contents.size() != 1) {
            throw new IllegalStateException("This response contains multiple content blocks; use contents() instead");
        }
        return contents.get(0);
    }

    public List<Content> contents() {
        return contents;
    }

    public String model() {
        return model;
    }

    public Role role() {
        return role;
    }

    public String stopReason() {
        return stopReason;
    }

    public Meta meta() {
        return meta;
    }
}
