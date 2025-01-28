package io.quarkiverse.mcp.server.runtime;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.ResourceContentsEncoder;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.runtime.FeatureMetadata.Feature;
import io.smallrye.mutiny.Uni;
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
    protected Object wrapResult(Object ret, FeatureMetadata<ResourceResponse> metadata, ArgumentProviders argProviders) {
        if (metadata.resultMapper() instanceof EncoderMapper) {
            // We need to wrap the returned value with ResourceContentsData
            // Supported variants are Uni<X>, List<X>, Uni<List<X>
            if (ret instanceof Uni<?> uni) {
                return uni.map(i -> {
                    if (i instanceof List<?> list) {
                        return list.stream().map(
                                e -> new ResourceContentsEncoder.ResourceContentsData<>(new RequestUri(argProviders.uri()), e))
                                .toList();
                    }
                    return new ResourceContentsEncoder.ResourceContentsData<>(new RequestUri(argProviders.uri()), i);
                });
            } else if (ret instanceof List<?> list) {
                return list.stream()
                        .map(e -> new ResourceContentsEncoder.ResourceContentsData<>(new RequestUri(argProviders.uri()), e))
                        .toList();
            }
            return new ResourceContentsEncoder.ResourceContentsData<>(new RequestUri(argProviders.uri()), ret);
        }
        return super.wrapResult(ret, metadata, argProviders);
    }

    @Override
    protected Object[] prepareArguments(FeatureMetadata<?> metadata, ArgumentProviders argProviders) throws McpException {
        if (metadata.feature() == Feature.RESOURCE_TEMPLATE) {
            // Use variable matching to extract method arguments
            Map<String, Object> matchedVariables = resourceTemplateManager.getVariableMatcher(metadata.info().name())
                    .matchVariables(argProviders.uri());
            argProviders = new ArgumentProviders(
                    matchedVariables, argProviders.connection(), argProviders.requestId(), argProviders.uri(),
                    argProviders.responder());
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
