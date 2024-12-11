package io.quarkiverse.mcp.server;

public record TextContent(String text) implements Content {

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
    public ResourceContent asResource() {
        throw new IllegalArgumentException("Not a resource");
    }

}
