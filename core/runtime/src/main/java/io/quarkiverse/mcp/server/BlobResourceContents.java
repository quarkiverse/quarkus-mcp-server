package io.quarkiverse.mcp.server;

import java.util.Base64;
import java.util.Map;

/**
 * Binary data of a resource.
 *
 * @param uri (must not be {@code null})
 * @param blob a base64-encoded string representing the binary data of the item (must not be {@code null})
 * @param mimeType the mime type of this resource (may be {@code null})
 * @param _meta the optional metadata (may be {@code null})
 */
public record BlobResourceContents(String uri, String blob, String mimeType, Map<MetaKey, Object> _meta)
        implements
            ResourceContents {

    /**
     * @param uri
     * @param blob
     * @return a new binary resource contents
     */
    public static BlobResourceContents create(String uri, String blob) {
        return new BlobResourceContents(uri, blob, null, null);
    }

    /**
     * @param uri
     * @param blob
     * @return a new binary resource contents
     */
    public static BlobResourceContents create(String uri, byte[] blob) {
        return new BlobResourceContents(uri, Base64.getMimeEncoder().encodeToString(blob), null, null);
    }

    public BlobResourceContents(String uri, String blob, String mimeType) {
        this(uri, blob, mimeType, null);
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
    public BlobResourceContents asBlob() {
        return this;
    }

}
