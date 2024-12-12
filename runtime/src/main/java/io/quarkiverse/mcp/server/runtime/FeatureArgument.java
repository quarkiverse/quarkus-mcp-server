package io.quarkiverse.mcp.server.runtime;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record FeatureArgument(String name, String description, boolean required, @JsonIgnore java.lang.reflect.Type type,
        @JsonIgnore Provider provider) {

    public enum Provider {
        PARAMS,
        REQUEST_ID,
        MCP_CONNECTION,
    }
}