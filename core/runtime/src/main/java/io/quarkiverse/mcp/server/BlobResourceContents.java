package io.quarkiverse.mcp.server;

import java.util.Base64;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 *
 * @param uri
 * @param blob A base64-encoded string representing the binary data of the item
 * @param mimeType
 */
@JsonInclude(Include.NON_NULL)
public record BlobResourceContents(String uri, String blob, String mimeType) implements ResourceContents {

    public static BlobResourceContents create(String uri, String blob) {
        return new BlobResourceContents(uri, blob, null);
    }

    public static BlobResourceContents create(String uri, byte[] blob) {
        return new BlobResourceContents(uri, Base64.getMimeEncoder().encodeToString(blob), null);
    }

    public BlobResourceContents {
        if (uri == null) {
            throw new IllegalArgumentException("uri must not be null");
        }
        if (blob == null) {
            throw new IllegalArgumentException("blob must not be null");
        }
    }

    @Override
    public Type type() {
        return Type.BLOB;
    }

    @Override
    public TextResourceContents asText() {
        throw new IllegalArgumentException("Not a text");
    }

    @Override
    public BlobResourceContents asBlob() {
        return this;
    }

}
