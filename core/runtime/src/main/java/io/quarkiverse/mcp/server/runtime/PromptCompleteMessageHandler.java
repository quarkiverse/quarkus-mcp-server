package io.quarkiverse.mcp.server.runtime;

import java.util.Objects;

import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.runtime.FeatureManagerBase.FeatureExecutionContext;
import io.vertx.core.Future;

class PromptCompleteMessageHandler extends CompletionMessageHandler {

    private final PromptCompletionManagerImpl manager;

    PromptCompleteMessageHandler(PromptCompletionManagerImpl manager) {
        super(manager.responseHandlers);
        this.manager = Objects.requireNonNull(manager);
    }

    @Override
    protected Future<CompletionResponse> execute(String key, ArgumentProviders argProviders, SecuritySupport securitySupport)
            throws McpException {
        return manager.execute(key, new FeatureExecutionContext(argProviders, securitySupport));
    }

}
