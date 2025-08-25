package io.quarkiverse.mcp.server;

import java.util.Map;

/**
 * A text content provided to or from an LLM.
 *
 * @param text (must not be {@code null})
 * @param _meta the optional metadata
 */
public record TextContent(String text, Map<MetaKey, Object> _meta) implements Content {

    public TextContent(String text) {
        this(text, null);
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
