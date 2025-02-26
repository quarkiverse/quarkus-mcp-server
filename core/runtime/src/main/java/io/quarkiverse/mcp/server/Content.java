package io.quarkiverse.mcp.server;

import com.fasterxml.jackson.annotation.JsonProperty;

public sealed interface Content permits TextContent, ImageContent, EmbeddedResource {

    @JsonProperty("type")
    default String getType() {
        return type().toString().toLowerCase();
    }

    Type type();

    TextContent asText();

    ImageContent asImage();

    EmbeddedResource asResource();

    enum Type {
        TEXT,
        IMAGE,
        RESOURCE
    }

}
