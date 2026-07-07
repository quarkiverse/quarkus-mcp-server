package io.quarkiverse.mcp.server.runtime;

import java.util.Optional;

import io.quarkiverse.mcp.server.RawMessage;
import io.quarkiverse.mcp.server.RequestId;
import io.vertx.core.json.JsonObject;

class RawMessageImpl implements RawMessage {

    static RawMessageImpl from(ArgumentProviders argProviders) {
        return new RawMessageImpl(argProviders.rawMessage());
    }

    private final JsonObject message;

    private volatile String cachedString;
    private volatile Optional<RequestId> cachedId;

    RawMessageImpl(JsonObject message) {
        this.message = message;
    }

    @Override
    public JsonObject asJsonObject() {
        // Encode and re-parse instead of copy() because the JsonObject may contain
        // non-Vert.x types (e.g. Jackson ObjectNode) that copy() cannot deep-copy
        return new JsonObject(message.encode());
    }

    @Override
    public String asString() {
        String s = cachedString;
        if (s == null) {
            s = message.encode();
            cachedString = s;
        }
        return s;
    }

    @Override
    public String method() {
        return message.getString("method");
    }

    @Override
    public RequestId id() {
        Optional<RequestId> id = cachedId;
        if (id == null) {
            Object val = message.getValue("id");
            id = val != null ? Optional.of(new RequestId(val)) : Optional.empty();
            cachedId = id;
        }
        return id.orElse(null);
    }

}
