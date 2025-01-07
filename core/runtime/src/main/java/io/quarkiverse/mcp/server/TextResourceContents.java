package io.quarkiverse.mcp.server;

/**
 *
 * @param uri
 * @param text
 * @param mimeType
 */
public record TextResourceContents(String uri, String text, String mimeType) implements ResourceContents {

    public static TextResourceContents create(String uri, String text) {
        return new TextResourceContents(uri, text, null);
    }

    @Override
    public Type type() {
        return Type.TEXT;
    }

    @Override
    public TextResourceContents asText() {
        return this;
    }

    @Override
    public BlobResourceContents asBlob() {
        throw new IllegalArgumentException("Not a blob");
    }

}
