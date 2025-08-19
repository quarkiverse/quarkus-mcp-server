package io.quarkiverse.mcp.server.runtime;

import io.quarkiverse.mcp.server.Meta;
import io.quarkiverse.mcp.server.MetaKey;
import io.vertx.core.json.JsonObject;

public class MetaImpl implements Meta {

    static MetaImpl from(JsonObject json) {
        if (json != null) {
            return new MetaImpl(json.getJsonObject("_meta"));
        }
        return new MetaImpl(null);
    }

    private final JsonObject meta;

    private MetaImpl(JsonObject meta) {
        this.meta = meta;
    }

    @Override
    public Object getValue(MetaKey key) {
        if (meta == null) {
            return null;
        }
        return meta.getValue(key.toString());
    }

    @Override
    public JsonObject asJsonObject() {
        // Return a copy since the JsonObject is mutable
        return meta == null ? new JsonObject() : meta.copy();
    }

}
