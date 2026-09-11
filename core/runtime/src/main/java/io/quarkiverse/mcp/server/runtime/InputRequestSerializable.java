package io.quarkiverse.mcp.server.runtime;

import io.vertx.core.json.JsonObject;

/**
 * Implemented by request types that can be serialized as an entry in the {@code inputRequests} map of an
 * {@code input_required} JSON-RPC result.
 *
 * @see io.quarkiverse.mcp.server.InputRequiredException
 */
public sealed interface InputRequestSerializable
        permits ElicitationRequestImpl, UrlElicitationRequestImpl, SamplingRequestImpl {

    /**
     * @return the JSON-RPC representation of this input request, with {@code method} and {@code params} keys
     */
    JsonObject toInputRequestJson();

}
