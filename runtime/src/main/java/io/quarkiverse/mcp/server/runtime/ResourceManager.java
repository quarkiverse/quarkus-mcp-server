package io.quarkiverse.mcp.server.runtime;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.ResourceResponse;
import io.vertx.core.Vertx;

@Singleton
public class ResourceManager extends FeatureManager<ResourceResponse> {

    final Map<String, FeatureMetadata<ResourceResponse>> resources;

    ResourceManager(McpMetadata metadata, Vertx vertx, ObjectMapper mapper) {
        super(vertx, mapper);
        this.resources = metadata.resources().stream().collect(Collectors.toMap(m -> m.info().uri(), Function.identity()));
    }

    @Override
    protected FeatureMetadata<ResourceResponse> getMetadata(String identifier) {
        return resources.get(identifier);
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
