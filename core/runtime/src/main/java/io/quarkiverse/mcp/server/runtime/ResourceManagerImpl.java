package io.quarkiverse.mcp.server.runtime;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.RequestId;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.ResourceContentsEncoder;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceManager.ResourceInfo;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

@Singleton
public class ResourceManagerImpl extends FeatureManagerBase<ResourceResponse, ResourceInfo> implements ResourceManager {

    final ResourceTemplateManagerImpl resourceTemplateManager;

    // uri -> resource
    final ConcurrentMap<String, ResourceInfo> resources;

    ResourceManagerImpl(McpMetadata metadata, Vertx vertx, ObjectMapper mapper,
            ResourceTemplateManagerImpl resourceTemplateManager, ConnectionManager connectionManager) {
        super(vertx, mapper, connectionManager);
        this.resourceTemplateManager = resourceTemplateManager;
        this.resources = new ConcurrentHashMap<>();
        for (FeatureMetadata<ResourceResponse> f : metadata.resources()) {
            this.resources.put(f.info().uri(), new ResourceMethod(f));
        }
    }

    @Override
    Stream<ResourceInfo> infoStream() {
        return resources.values().stream();
    }

    @Override
    public int size() {
        return resources.size();
    }

    @Override
    public ResourceInfo getResource(String uri) {
        return resources.get(uri);
    }

    @Override
    public ResourceDefinition newResource(String name) {
        for (ResourceInfo resource : resources.values()) {
            if (resource.name().equals(name)) {
                resourceWithNameAlreadyExists(name);
            }
        }
        return new ResourceDefinitionImpl(name);
    }

    IllegalArgumentException resourceWithNameAlreadyExists(String name) {
        return new IllegalArgumentException("A resource with name [" + name + "] already exits");
    }

    IllegalArgumentException resourceWithUriAlreadyExists(String uri) {
        return new IllegalArgumentException("A resource with uri [" + uri + "] already exits");
    }

    @Override
    public ResourceInfo removeResource(String uri) {
        return resources.computeIfPresent(uri, (key, value) -> {
            if (!value.isMethod()) {
                notifyConnections("notifications/resources/list_changed");
                return null;
            }
            return value;
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    protected FeatureInvoker<ResourceResponse> getInvoker(String id) {
        ResourceInfo resource = resources.get(id);
        if (resource instanceof FeatureInvoker fi) {
            return fi;
        }
        return resourceTemplateManager.getInvoker(id);
    }

    @Override
    protected Object wrapResult(Object ret, FeatureMetadata<?> metadata, ArgumentProviders argProviders) {
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
    protected McpException notFound(String id) {
        return new McpException("Invalid resource uri: " + id, JsonRPC.RESOURCE_NOT_FOUND);
    }

    class ResourceMethod extends FeatureMetadataInvoker<ResourceResponse> implements ResourceManager.ResourceInfo {

        private ResourceMethod(FeatureMetadata<ResourceResponse> metadata) {
            super(metadata);
        }

        @Override
        public String name() {
            return metadata.info().name();
        }

        @Override
        public String description() {
            return metadata.info().description();
        }

        @Override
        public String uri() {
            return metadata.info().uri();
        }

        @Override
        public String mimeType() {
            return metadata.info().mimeType();
        }

        @Override
        public boolean isMethod() {
            return true;
        }

        @Override
        public JsonObject asJson() {
            return metadata.asJson();
        }

    }

    class ResourceDefinitionInfo extends FeatureManagerBase.FeatureDefinitionInfoBase<ResourceArguments, ResourceResponse>
            implements ResourceManager.ResourceInfo {

        private final String uri;
        private final String mimeType;

        private ResourceDefinitionInfo(String name, String description, Function<ResourceArguments, ResourceResponse> fun,
                Function<ResourceArguments, Uni<ResourceResponse>> asyncFun, boolean runOnVirtualThread, String uri,
                String mimeType) {
            super(name, description, fun, asyncFun, runOnVirtualThread);
            this.uri = uri;
            this.mimeType = mimeType;
        }

        @Override
        public String uri() {
            return uri;
        }

        @Override
        public String mimeType() {
            return mimeType;
        }

        @Override
        public JsonObject asJson() {
            return new JsonObject().put("name", name())
                    .put("description", description())
                    .put("uri", uri())
                    .put("mimeType", mimeType());
        }

        @Override
        protected ResourceArguments createArguments(ArgumentProviders argumentProviders) {
            return new ResourceArguments(
                    argumentProviders.connection(),
                    log(Feature.RESOURCE.toString().toLowerCase() + ":" + name, name, argumentProviders),
                    new RequestId(argumentProviders.requestId()),
                    new RequestUri(argumentProviders.uri()));
        }

    }

    class ResourceDefinitionImpl extends
            FeatureManagerBase.FeatureDefinitionBase<ResourceInfo, ResourceArguments, ResourceResponse, ResourceDefinitionImpl>
            implements ResourceManager.ResourceDefinition {

        private String uri;
        private String mimeType;

        ResourceDefinitionImpl(String name) {
            super(name);
        }

        @Override
        public ResourceDefinition setUri(String uri) {
            if (resources.containsKey(uri)) {
                throw resourceWithUriAlreadyExists(uri);
            }
            this.uri = Objects.requireNonNull(uri);
            return this;
        }

        @Override
        public ResourceDefinition setMimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        @Override
        public ResourceInfo register() {
            validate();
            ResourceDefinitionInfo ret = new ResourceDefinitionInfo(name, description, fun, asyncFun,
                    runOnVirtualThread, uri, mimeType);
            ResourceInfo existing = resources.putIfAbsent(uri, ret);
            if (existing != null) {
                throw resourceWithUriAlreadyExists(uri);
            } else {
                notifyConnections("notifications/resources/list_changed");
            }
            return ret;
        }
    }

}
