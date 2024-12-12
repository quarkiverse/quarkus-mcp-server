package io.quarkiverse.mcp.server.runtime;

import java.util.function.Function;

import jakarta.enterprise.invoke.Invoker;

import io.smallrye.mutiny.Uni;

/**
 *
 * @param <M> The response message
 */
public record FeatureMetadata<M>(FeatureMethodInfo info, Invoker<Object, Object> invoker, ExecutionModel executionModel,
        Function<Object, Uni<M>> resultMapper) {

}
