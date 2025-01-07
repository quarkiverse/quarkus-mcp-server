package io.quarkiverse.mcp.server;

public record ImageContent(String data, String mimeType) implements Content {

    @Override
    public Type type() {
        return Type.IMAGE;
    }

    @Override
    public TextContent asText() {
        throw new IllegalArgumentException("Not a text");
    }

    @Override
    public ImageContent asImage() {
        return this;
    }

    @Override
    public ResourceContent asResource() {
        throw new IllegalArgumentException("Not a resource");
    }
}
