package io.quarkiverse.mcp.server.runtime;

import java.util.Map;
import java.util.function.Function;

import jakarta.enterprise.invoke.Invoker;

import io.quarkiverse.mcp.server.ExecutionModel;
import io.quarkiverse.mcp.server.runtime.ResultMappers.Result;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public record FeatureMetadata<M>(Feature feature,
        FeatureMethodInfo info,
        Invoker<Object, Object> invoker,
        ExecutionModel executionModel,
        Function<Result<Object>, Uni<M>> resultMapper) implements Comparable<FeatureMetadata<M>> {

    @Override
    public int compareTo(FeatureMetadata<M> o) {
        return info.name().compareTo(o.info.name());
    }

    public JsonObject asJson() {
        JsonObject ret = new JsonObject()
                .put("name", info.name())
                .put("description", info.description());
        if (info.title() != null) {
            ret.put("title", info.title());
        }
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
            if (info.size() > 0) {
                ret.put("size", info.size());
            }
            if (info.resourceAnnotations() != null) {
                ret.put("annotations", info.resourceAnnotations());
            }
        } else if (feature == Feature.RESOURCE_TEMPLATE) {
            ret.put("uriTemplate", info.uri());
            if (info.mimeType() != null) {
                ret.put("mimeType", info.mimeType());
            }
            if (info.resourceAnnotations() != null) {
                ret.put("annotations", info.resourceAnnotations());
            }
        }
        if (!info.metadata().isEmpty()) {
            JsonObject meta = new JsonObject();
            for (Map.Entry<String, String> e : info.metadata().entrySet()) {
                meta.put(e.getKey(), Json.decodeValue(e.getValue()));
            }
            ret.put("_meta", meta);
        }
        return ret;
    }

}
