package io.quarkiverse.mcp.server;

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

    @Override
    public ImageContent asImage() {
        throw new IllegalArgumentException("Not an image");
    }

    @Override
    public EmbeddedResource asResource() {
        throw new IllegalArgumentException("Not a resource");
    }

}
