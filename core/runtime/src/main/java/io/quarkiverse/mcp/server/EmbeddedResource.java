package io.quarkiverse.mcp.server;

/**
 * A resource embedded into a prompt or tool call result.
 *
 * @param resource (must not be {@code null})
 * @see Prompt
 * @see Tool
 */
public record EmbeddedResource(ResourceContents resource) implements Content {

    public EmbeddedResource {
        if (resource == null) {
            throw new IllegalArgumentException("resource must not be null");
        }
    }

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
    public EmbeddedResource asResource() {
        return this;
    }
}
