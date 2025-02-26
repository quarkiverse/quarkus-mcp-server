package io.quarkiverse.mcp.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 *
 * @param uri
 * @param text
 * @param mimeType
 */
@JsonInclude(Include.NON_NULL)
public record TextResourceContents(String uri, String text, String mimeType) implements ResourceContents {

    public static TextResourceContents create(String uri, String text) {
        return new TextResourceContents(uri, text, null);
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

    @Override
    public BlobResourceContents asBlob() {
        throw new IllegalArgumentException("Not a blob");
    }

}
