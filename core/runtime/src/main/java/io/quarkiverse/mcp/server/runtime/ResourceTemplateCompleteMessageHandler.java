package io.quarkiverse.mcp.server.runtime;

import java.util.Objects;

import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.runtime.FeatureManagerBase.FeatureExecutionContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

class ResourceTemplateCompleteMessageHandler extends CompletionMessageHandler {

    private final ResourceTemplateCompletionManagerImpl manager;

    ResourceTemplateCompleteMessageHandler(ResourceTemplateCompletionManagerImpl manager) {
        super(manager.responseHandlers);
        this.manager = Objects.requireNonNull(manager);
    }

    @Override
    protected String referenceName(JsonObject ref) {
        return ref.getString("uri");
    }

    @Override
    protected Future<CompletionResponse> execute(String key, ArgumentProviders argProviders, McpRequest mcpRequest)
            throws McpException {
        return manager.execute(key, new FeatureExecutionContext(argProviders, mcpRequest));
    }

}
