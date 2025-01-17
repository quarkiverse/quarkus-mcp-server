package io.quarkiverse.mcp.server.runtime;

import java.util.Objects;

import io.quarkiverse.mcp.server.CompletionResponse;
import io.vertx.core.Future;

class ResourceTemplateCompleteMessageHandler extends CompletionMessageHandler {

    private final ResourceTemplateCompleteManager manager;

    ResourceTemplateCompleteMessageHandler(ResourceTemplateCompleteManager manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    @Override
    protected Future<CompletionResponse> execute(String key, ArgumentProviders argProviders) throws McpException {
        return manager.execute(key, argProviders);
    }

}
