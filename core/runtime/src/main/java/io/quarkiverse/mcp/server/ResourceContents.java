package io.quarkiverse.mcp.server;

import java.util.Map;

/**
 * Represents text/binary data of a resource.
 */
public sealed interface ResourceContents permits TextResourceContents, BlobResourceContents {

    /**
     *
     * @return the type of the resource
     */
    Type type();

    /**
     * @return the optional metadata
     */
    Map<MetaKey, Object> _meta();

    /**
     * Casts and returns this object as text resource contents, or throws an {@link IllegalArgumentException} if the content
     * object does not represent a {@link TextResourceContents}.
     *
     * @return the text content
     */
    default TextResourceContents asText() {
        throw new IllegalArgumentException("Not a text");
    }

    /**
     * Casts and returns this object as binary resource contents, or throws an {@link IllegalArgumentException} if the content
     * object does not represent a {@link BlobResourceContents}.
     *
     * @return the binary content
     */
    default BlobResourceContents asBlob() {
        throw new IllegalArgumentException("Not a blob");
    }

    enum Type {
        TEXT,
        BLOB
    }

}
