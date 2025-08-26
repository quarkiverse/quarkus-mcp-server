package io.quarkiverse.mcp.server;

import java.util.Map;

/**
 * A text content provided to or from an LLM.
 *
 * @param text (must not be {@code null})
 * @param _meta the optional metadata (may be {@code null})
 * @param annotations the optional annotations (may be {@code null})
 */
public record TextContent(String text, Map<MetaKey, Object> _meta, Annotations annotations) implements Content {

    public TextContent(String text) {
        this(text, null, null);
    }

    public TextContent {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
    }

    @Override
    public Type type() {
        return Type.TEXT;
    }

    @Override
    public TextContent asText() {
        return this;
    }

}
