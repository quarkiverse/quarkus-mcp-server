package io.quarkiverse.mcp.server.runtime;

import jakarta.enterprise.invoke.Invoker;

public record PromptMetadata(PromptMethodInfo info, Invoker<Object, Object> invoker, ExecutionModel executionModel) {
}