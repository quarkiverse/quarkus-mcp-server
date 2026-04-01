package io.quarkiverse.mcp.server.runtime;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.PromptCompletionManager;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.vertx.core.Vertx;

@Singleton
public class PromptCompletionManagerImpl extends CompletionManagerBase implements PromptCompletionManager {

    private final PromptManagerImpl promptManager;

    protected PromptCompletionManagerImpl(McpMetadata metadata,
            Vertx vertx,
            ObjectMapper mapper,
            ConnectionManager connectionManager,
            PromptManagerImpl promptManager,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation,
            ResponseHandlers responseHandlers,
            CancellationRequests cancellationRequests,
            McpServersRuntimeConfig config) {
        super(vertx, mapper, connectionManager, currentIdentityAssociation, responseHandlers, cancellationRequests, config,
                metadata);
        this.promptManager = promptManager;
        for (FeatureMetadata<CompletionResponse> c : metadata.promptCompletions()) {
            String compositeKey = c.info().name() + "_"
                    + c.info().arguments().stream().filter(FeatureArgument::isParam).findFirst().orElseThrow().name();
            CompletionMethod completionMethod = new CompletionMethod(c);
            for (String server : c.info().serverNames()) {
                this.completions.put(new FeatureKey(compositeKey, server), completionMethod);
            }
        }
    }

    @Override
    protected McpException notFound(String id) {
        return new McpException("Prompt completion does not exist: " + id, JsonRpcErrorCodes.INVALID_PARAMS);
    }

    @Override
    protected Feature feature() {
        return Feature.PROMPT_COMPLETE;
    }

    @Override
    protected String refName(String refName) {
        return refName;
    }

    @Override
    protected void validateReference(String refName, String argumentName) {
        PromptManager.PromptInfo prompt = promptManager.getPrompt(refName);
        if (prompt == null) {
            throw new IllegalStateException("Prompt does not exist: " + refName);
        }
        for (PromptManager.PromptArgument a : prompt.arguments()) {
            if (a.name().equals(argumentName)) {
                return;
            }
        }
        throw new IllegalStateException("Prompt [" + refName + "] does not define argument: " + argumentName);
    }

}
