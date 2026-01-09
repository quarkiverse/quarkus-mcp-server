package io.quarkiverse.mcp.server.runtime;

import java.util.Objects;

import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.runtime.FeatureManagerBase.FeatureExecutionContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

class PromptCompleteMessageHandler extends CompletionMessageHandler {

    private final PromptCompletionManagerImpl manager;

    PromptCompleteMessageHandler(PromptCompletionManagerImpl manager) {
        super(manager.responseHandlers);
        this.manager = Objects.requireNonNull(manager);
    }

    @Override
    protected Future<CompletionResponse> execute(String key, JsonObject message, ArgumentProviders argProviders,
            McpRequest mcpRequest)
            throws McpException {
        return manager.execute(key, new FeatureExecutionContext(message, mcpRequest, argProviders));
    }

}
