package io.quarkiverse.mcp.server.runtime;

import io.quarkiverse.mcp.server.InputRequiredException.ElicitationInputRequest;
import io.quarkiverse.mcp.server.InputRequiredException.InputRequestEntry;
import io.quarkiverse.mcp.server.InputRequiredException.RootsInputRequest;
import io.quarkiverse.mcp.server.InputRequiredException.SamplingInputRequest;
import io.quarkiverse.mcp.server.InputRequiredException.UrlElicitationInputRequest;
import io.quarkiverse.mcp.server.McpMethod;
import io.vertx.core.json.JsonObject;

/**
 * Serializes {@link InputRequestEntry} instances into their JSON-RPC representation for the {@code inputRequests} map of an
 * {@code input_required} result.
 */
public final class InputRequestSupport {

    private InputRequestSupport() {
    }

    public static JsonObject toInputRequestJson(InputRequestEntry entry) {
        if (entry instanceof ElicitationInputRequest e) {
            return serialize(e.request());
        } else if (entry instanceof UrlElicitationInputRequest e) {
            return serialize(e.request());
        } else if (entry instanceof SamplingInputRequest e) {
            return serialize(e.request());
        } else if (entry instanceof RootsInputRequest) {
            return new JsonObject()
                    .put("method", McpMethod.ROOTS_LIST.jsonRpcName())
                    .put("params", new JsonObject());
        }
        throw new IllegalArgumentException("Unknown input request entry type: " + entry.getClass().getName());
    }

    private static JsonObject serialize(Object request) {
        if (request instanceof InputRequestSerializable serializable) {
            return serializable.toInputRequestJson();
        }
        throw new IllegalArgumentException("Unsupported input request implementation: " + request.getClass().getName());
    }

}
