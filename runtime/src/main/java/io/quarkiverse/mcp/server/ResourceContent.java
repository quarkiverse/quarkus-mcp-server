package io.quarkiverse.mcp.server;

public record ResourceContent(String uri, String mimeType, String text) implements Content {

    @Override
    public Type type() {
        return Type.RESOURCE;
    }

    @Override
    public TextContent asText() {
        throw new IllegalArgumentException("Not a text");
    }

    @Override
    public ImageContent asImage() {
        throw new IllegalArgumentException("Not an image");
    }

    @Override
    public ResourceContent asResource() {
        return this;
    }
}
