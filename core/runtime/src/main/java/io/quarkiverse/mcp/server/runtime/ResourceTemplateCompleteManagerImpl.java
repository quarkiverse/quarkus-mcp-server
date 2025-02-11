package io.quarkiverse.mcp.server.runtime;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.ResourceTemplateCompletionManager;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateManagerImpl.ResourceTemplateMetadata;
import io.vertx.core.Vertx;

@Singleton
public class ResourceTemplateCompleteManagerImpl extends CompletionManagerBase implements ResourceTemplateCompletionManager {

    private final ResourceTemplateManagerImpl resourceTemplateManager;

    ResourceTemplateCompleteManagerImpl(McpMetadata metadata, Vertx vertx, ObjectMapper mapper,
            ConnectionManager connectionManager, ResourceTemplateManagerImpl resourceTemplateManager) {
        super(vertx, mapper, connectionManager);
        for (FeatureMetadata<CompletionResponse> c : metadata.resourceTemplateCompletions()) {
            String key = c.info().name() + "_"
                    + c.info().arguments().stream().filter(FeatureArgument::isParam).findFirst().orElseThrow()
                            .name();
            this.completions.put(key, new CompletionMethod(c));
        }
        this.resourceTemplateManager = resourceTemplateManager;
    }

    @Override
    protected McpException notFound(String id) {
        return new McpException("Resource template completion does not exist: " + id, JsonRPC.INVALID_PARAMS);
    }

    @Override
    protected Feature feature() {
        return Feature.RESOURCE_TEMPLATE_COMPLETE;
    }

    @Override
    protected void validateReference(String refName, String argumentName) {
        ResourceTemplateMetadata templateMeta = resourceTemplateManager.templates.get(refName);
        if (templateMeta == null) {
            throw new IllegalStateException("Resource template does not exist: " + refName);
        }
        if (!templateMeta.variableMatcher().variables().contains(argumentName)) {
            throw new IllegalStateException("Resource template [" + refName + "] does not define argument: " + argumentName);
        }
    }

}
