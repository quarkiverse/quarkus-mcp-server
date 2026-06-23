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
        // Return a copy since the JsonObject is mutable
        return message.copy();
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
