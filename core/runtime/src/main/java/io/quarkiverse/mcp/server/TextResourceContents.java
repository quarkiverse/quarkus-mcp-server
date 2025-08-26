package io.quarkiverse.mcp.server;

import java.util.Map;

/**
 * Text data of a resource.
 *
 * @param uri (must not be {@code null})
 * @param text (must not be {@code null})
 * @param mimeType the mime type of this resource (may be {@code null})
 * @param _meta the optional metadata (may be {@code null})
 */
public record TextResourceContents(String uri, String text, String mimeType, Map<MetaKey, Object> _meta)
        implements
            ResourceContents {

    /**
     * @param uri
     * @param text
     * @return a new text resource contents
     */
    public static TextResourceContents create(String uri, String text) {
        return new TextResourceContents(uri, text, null, null);
    }

    public TextResourceContents(String uri, String text, String mimeType) {
        this(uri, text, mimeType, null);
    }

    public TextResourceContents {
        if (uri == null) {
            throw new IllegalArgumentException("uri must not be null");
        }
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
    }

    @Override
    public Type type() {
        return Type.TEXT;
    }

    @Override
    public TextResourceContents asText() {
        return this;
    }

}
