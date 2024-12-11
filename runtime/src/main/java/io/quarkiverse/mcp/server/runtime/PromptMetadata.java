package io.quarkiverse.mcp.server.runtime;

import java.util.List;
import java.util.function.Function;

import jakarta.enterprise.invoke.Invoker;

import io.quarkiverse.mcp.server.PromptMessage;
import io.smallrye.mutiny.Uni;

public record PromptMetadata(PromptMethodInfo info, Invoker<Object, Object> invoker, ExecutionModel executionModel,
        Function<Object, Uni<List<PromptMessage>>> resultMapper) {

}
