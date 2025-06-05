package io.quarkiverse.mcp.server.runtime;

import java.util.function.Function;

import jakarta.enterprise.invoke.Invoker;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 *
 * @param <M> The response message
 */
public record FeatureMetadata<M>(Feature feature,
        FeatureMethodInfo info,
        Invoker<Object, Object> invoker,
        ExecutionModel executionModel,
        Function<Object, Uni<M>> resultMapper) implements Comparable<FeatureMetadata<M>> {

    @Override
    public int compareTo(FeatureMetadata<M> o) {
        return info.name().compareTo(o.info.name());
    }

    public JsonObject asJson() {
        JsonObject ret = new JsonObject()
                .put("name", info.name())
                .put("description", info.description());
        if (feature == Feature.PROMPT) {
            JsonArray arguments = new JsonArray();
            for (FeatureArgument arg : info.serializedArguments()) {
                arguments.add(arg.asJson());
            }
            ret.put("arguments", arguments);
        } else if (feature == Feature.RESOURCE) {
            ret.put("uri", info.uri());
            if (info.mimeType() != null) {
                ret.put("mimeType", info.mimeType());
            }
        } else if (feature == Feature.RESOURCE_TEMPLATE) {
            ret.put("uriTemplate", info.uri());
            if (info.mimeType() != null) {
                ret.put("mimeType", info.mimeType());
            }
        }
        return ret;
    }

}
