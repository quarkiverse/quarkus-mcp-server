package io.quarkiverse.mcp.server.runtime;

import io.vertx.core.json.JsonObject;

/**
 * Implemented by request types that can be serialized as an entry in the {@code inputRequests} map of an
 * {@code input_required} JSON-RPC result.
 * <p>
 * This is an internal serialization SPI; input request entries are dispatched to it by {@link InputRequestSupport}.
 *
 * @see io.quarkiverse.mcp.server.InputRequiredException
 * @see InputRequestSupport
 */
sealed interface InputRequestSerializable
        permits ElicitationRequestImpl, UrlElicitationRequestImpl, SamplingRequestImpl {

    /**
     * @return the JSON-RPC representation of this input request, with {@code method} and {@code params} keys
     */
    JsonObject toInputRequestJson();

}
