package io.quarkiverse.mcp.server;

/**
 * A text content provided to or from an LLM.
 *
 * @param text (must not be {@code null})
 */
public record TextContent(String text) implements Content {

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
