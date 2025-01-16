package io.quarkiverse.mcp.server.runtime;

import io.vertx.core.json.JsonObject;

public record FeatureArgument(String name, String description, boolean required, java.lang.reflect.Type type,
        Provider provider) {

    public JsonObject asJson() {
        return new JsonObject()
                .put("name", name)
                .put("description", description)
                .put("required", required);
    }

    public boolean isParam() {
        return provider == Provider.PARAMS;
    }

    public enum Provider {
        PARAMS,
        REQUEST_ID,
        MCP_CONNECTION,
        MCP_LOG
    }
}