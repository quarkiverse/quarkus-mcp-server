package io.quarkiverse.mcp.server.runtime;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.runtime.FeatureMetadata.Feature;
import io.vertx.core.Vertx;

@Singleton
public class ResourceManager extends FeatureManager<ResourceResponse> {

    final ResourceTemplateManager resourceTemplateManager;

    final Map<String, FeatureMetadata<ResourceResponse>> resources;

    ResourceManager(McpMetadata metadata, Vertx vertx, ObjectMapper mapper, ResourceTemplateManager resourceTemplateManager) {
        super(vertx, mapper);
        this.resourceTemplateManager = resourceTemplateManager;
        this.resources = metadata.resources().stream().collect(Collectors.toMap(m -> m.info().uri(), Function.identity()));
    }

    @Override
    protected FeatureMetadata<ResourceResponse> getMetadata(String identifier) {
        FeatureMetadata<ResourceResponse> ret = resources.get(identifier);
        if (ret == null) {
            ret = resourceTemplateManager.getMetadata(identifier);
        }
        return ret;
    }

    @Override
    protected Object[] prepareArguments(FeatureMetadata<?> metadata, ArgumentProviders argProviders) throws McpException {
        if (metadata.feature() == Feature.RESOURCE_TEMPLATE) {
            // Use variable matching to extract method arguments
            Map<String, Object> matchedVariables = resourceTemplateManager.getVariableMatcher(metadata.info().name())
                    .matchVariables(argProviders.args().get("uri").toString());
            matchedVariables.putIfAbsent("uri", argProviders.args().get("uri"));
            argProviders = new ArgumentProviders(
                    matchedVariables, argProviders.connection(), argProviders.requestId(), argProviders.responder());
        }
        return super.prepareArguments(metadata, argProviders);
    }

    /**
     *
     * @return the list of resources sorted by name asc
     */
    public List<FeatureMetadata<ResourceResponse>> list() {
        return resources.values().stream().sorted().toList();
    }

    @Override
    protected McpException notFound(String id) {
        return new McpException("Invalid resource uri: " + id, JsonRPC.RESOURCE_NOT_FOUND);
    }

}
