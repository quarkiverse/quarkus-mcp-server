package io.quarkiverse.mcp.server;

public sealed interface ResourceContents permits TextResourceContents, BlobResourceContents {

    Type type();

    TextResourceContents asText();

    BlobResourceContents asBlob();

    enum Type {
        TEXT,
        BLOB
    }

}
