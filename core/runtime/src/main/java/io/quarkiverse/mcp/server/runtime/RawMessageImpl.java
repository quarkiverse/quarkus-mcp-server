package io.quarkiverse.mcp.server.runtime;

import io.quarkiverse.mcp.server.RawMessage;
import io.vertx.core.json.JsonObject;

class RawMessageImpl implements RawMessage {

    static RawMessageImpl from(ArgumentProviders argProviders) {
        return new RawMessageImpl(argProviders.rawMessage());
    }

    private final JsonObject message;

    private RawMessageImpl(JsonObject message) {
        this.message = message;
    }

    @Override
    public JsonObject asJsonObject() {
        // Return a copy since the JsonObject is mutable
        return message.copy();
    }

    @Override
    public String asString() {
        return message.encode();
    }

}
