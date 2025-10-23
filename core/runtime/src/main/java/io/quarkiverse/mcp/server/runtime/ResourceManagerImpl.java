package io.quarkiverse.mcp.server.runtime;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.Content.Annotations;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.ResourceContentsEncoder;
import io.quarkiverse.mcp.server.ResourceFilter;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceManager.ResourceInfo;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkus.arc.All;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

@Singleton
public class ResourceManagerImpl extends FeatureManagerBase<ResourceResponse, ResourceInfo> implements ResourceManager {

    private static final Logger LOG = Logger.getLogger(ResourceManagerImpl.class);

    final ResourceTemplateManagerImpl resourceTemplateManager;

    final ConcurrentMap<String, ResourceInfo> uriToResource;
    final Set<String> resourceNames;

    // uri -> subscribers (connection ids)
    final ConcurrentMap<String, List<String>> uriToSubscribers;

    final List<ResourceFilter> filters;

    ResourceManagerImpl(McpMetadata metadata,
            Vertx vertx,
            ObjectMapper mapper,
            ResourceTemplateManagerImpl resourceTemplateManager,
            ConnectionManager connectionManager,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation,
            ResponseHandlers responseHandlers,
            @All List<ResourceFilter> filters) {
        super(vertx, mapper, connectionManager, currentIdentityAssociation, responseHandlers);
        this.resourceTemplateManager = resourceTemplateManager;
        this.uriToResource = new ConcurrentHashMap<>();
        this.resourceNames = Collections.synchronizedSet(new HashSet<>());
        this.uriToSubscribers = new ConcurrentHashMap<>();
        for (FeatureMetadata<ResourceResponse> f : metadata.resources()) {
            this.uriToResource.put(f.info().uri(), new ResourceMethod(f));
            this.resourceNames.add(f.info().name());
        }
        this.filters = filters;
    }

    @Override
    Stream<ResourceInfo> infos() {
        return uriToResource.values().stream();
    }

    @Override
    Stream<ResourceInfo> filter(Stream<ResourceInfo> infos, McpConnection connection) {
        return infos.filter(r -> test(r, connection));
    }

    @Override
    public ResourceInfo getResource(String uri) {
        return uriToResource.get(Objects.requireNonNull(uri));
    }

    void subscribe(String uri, McpRequest mcpRequest) {
        ResourceInfo info = getResource(uri);
        if (info == null || !matches(info, mcpRequest)) {
            throw notFound(uri);
        }
        List<String> ids = new CopyOnWriteArrayList<>();
        ids.add(mcpRequest.connection().id());
        uriToSubscribers.merge(uri, ids, (old, val) -> Stream.concat(old.stream(), val.stream())
                .collect(Collectors.toCollection(CopyOnWriteArrayList::new)));
    }

    void unsubscribe(String uri, String connectionId) {
        List<String> ids = uriToSubscribers.get(uri);
        if (ids != null) {
            ids.remove(connectionId);
        }
    }

    @Override
    public ResourceDefinition newResource(String name) {
        for (ResourceInfo resource : uriToResource.values()) {
            if (resource.name().equals(name)) {
                resourceWithNameAlreadyExists(name);
            }
        }
        return new ResourceDefinitionImpl(name);
    }

    private void sendUpdateNotifications(String uri) {
        JsonObject updated = Messages.newNotification("notifications/resources/updated", new JsonObject().put("uri", uri));
        List<String> ids = uriToSubscribers.get(uri);
        if (ids != null) {
            for (String connectionId : ids) {
                McpConnectionBase connection = connectionManager.get(connectionId);
                if (connection != null) {
                    connection.send(updated);
                } else {
                    unsubscribe(uri, connectionId);
                }
            }
        }
    }

    IllegalArgumentException resourceWithNameAlreadyExists(String name) {
        return new IllegalArgumentException("A resource with name [" + name + "] already exits");
    }

    IllegalArgumentException resourceWithUriAlreadyExists(String uri) {
        return new IllegalArgumentException("A resource with uri [" + uri + "] already exits");
    }

    @Override
    public ResourceInfo removeResource(String uri) {
        AtomicReference<ResourceInfo> removed = new AtomicReference<>();
        uriToResource.computeIfPresent(uri, (key, value) -> {
            if (!value.isMethod()) {
                removed.set(value);
                resourceNames.remove(value.name());
                notifyConnections("notifications/resources/list_changed");
                return null;
            }
            return value;
        });
        return removed.get();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected FeatureInvoker<ResourceResponse> getInvoker(String id, McpRequest mcpRequest) {
        ResourceInfo resource = uriToResource.get(id);
        if (resource instanceof FeatureInvoker fi
                && matches(resource, mcpRequest)
                && test(resource, mcpRequest.connection())) {
            return fi;
        }
        return resourceTemplateManager.getInvoker(id, mcpRequest);
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
        return new McpException("Resource not found: " + id, JsonRpcErrorCodes.RESOURCE_NOT_FOUND);
    }

    private boolean test(ResourceInfo resource, McpConnection connection) {
        if (filters.isEmpty()) {
            return true;
        }
        for (ResourceFilter filter : filters) {
            try {
                if (!filter.test(resource, connection)) {
                    return false;
                }
            } catch (RuntimeException e) {
                LOG.errorf(e, "Unable to apply filter: %s", filter);
            }
        }
        return true;
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
        public String title() {
            return metadata.info().title();
        }

        @Override
        public String description() {
            return metadata.info().description();
        }

        @Override
        public String serverName() {
            return metadata.info().serverName();
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
        public OptionalInt size() {
            return metadata.info().size() > 0 ? OptionalInt.of(metadata.info().size()) : OptionalInt.empty();
        }

        @Override
        public Optional<Annotations> annotations() {
            return Optional.ofNullable(metadata.info().resourceAnnotations());
        }

        @Override
        public boolean isMethod() {
            return true;
        }

        @Override
        public JsonObject asJson() {
            return metadata.asJson();
        }

        @Override
        public void sendUpdateAndForget() {
            ResourceManagerImpl.this.sendUpdateNotifications(uri());
        }

    }

    class ResourceDefinitionInfo extends FeatureManagerBase.FeatureDefinitionInfoBase<ResourceArguments, ResourceResponse>
            implements ResourceManager.ResourceInfo {

        private final String title;
        private final String uri;
        private final String mimeType;
        private final int size;
        private final Content.Annotations annotations;

        private ResourceDefinitionInfo(String name, String title, String description, String serverName,
                Function<ResourceArguments, ResourceResponse> fun,
                Function<ResourceArguments, Uni<ResourceResponse>> asyncFun, boolean runOnVirtualThread, String uri,
                String mimeType, int size, Content.Annotations annotations) {
            super(name, description, serverName, fun, asyncFun, runOnVirtualThread);
            this.title = title;
            this.uri = uri;
            this.mimeType = mimeType;
            this.size = size;
            this.annotations = annotations;
        }

        @Override
        public String title() {
            return title;
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
        public OptionalInt size() {
            return size > 0 ? OptionalInt.of(size) : OptionalInt.empty();
        }

        @Override
        public Optional<Annotations> annotations() {
            return Optional.ofNullable(annotations);
        }

        @Override
        public JsonObject asJson() {
            JsonObject ret = new JsonObject().put("name", name())
                    .put("description", description())
                    .put("uri", uri);
            if (mimeType != null) {
                ret.put("mimeType", mimeType);
            }
            if (title != null) {
                ret.put("title", title);
            }
            if (size > 0) {
                ret.put("size", size);
            }
            if (annotations != null) {
                ret.put("annotations", annotations);
            }
            return ret;
        }

        @Override
        protected ResourceArguments createArguments(ArgumentProviders argumentProviders) {
            return new ResourceArgumentsImpl(
                    argumentProviders,
                    new RequestUri(argumentProviders.uri()),
                    log(Feature.RESOURCE.toString().toLowerCase() + ":" + name, name, argumentProviders));
        }

        @Override
        public void sendUpdateAndForget() {
            ResourceManagerImpl.this.sendUpdateNotifications(uri());
        }
    }

    static class ResourceArgumentsImpl extends AbstractRequestFeatureArguments implements ResourceArguments {

        private final RequestUri requestUri;
        private final McpLog log;

        ResourceArgumentsImpl(ArgumentProviders argProviders, RequestUri requestUri, McpLog log) {
            super(argProviders);
            this.requestUri = requestUri;
            this.log = log;
        }

        @Override
        public McpLog log() {
            return log;
        }

        @Override
        public RequestUri requestUri() {
            return requestUri;
        }

    }

    class ResourceDefinitionImpl extends
            FeatureManagerBase.FeatureDefinitionBase<ResourceInfo, ResourceArguments, ResourceResponse, ResourceDefinitionImpl>
            implements ResourceManager.ResourceDefinition {

        private String title;
        private String uri;
        private String mimeType;
        private int size = -1;
        private Content.Annotations annotations;

        ResourceDefinitionImpl(String name) {
            super(name);
        }

        @Override
        public ResourceDefinition setTitle(String title) {
            this.title = title;
            return this;
        }

        @Override
        public ResourceDefinition setUri(String uri) {
            if (uriToResource.containsKey(uri)) {
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
        public ResourceDefinition setSize(int size) {
            this.size = size;
            return this;
        }

        @Override
        public ResourceDefinition setAnnotations(Annotations annotations) {
            this.annotations = annotations;
            return this;
        }

        @Override
        public ResourceInfo register() {
            validate();
            ResourceInfo newValue = uriToResource.compute(uri, (uri, old) -> {
                if (old != null) {
                    throw resourceWithUriAlreadyExists(uri);
                }
                if (resourceNames.contains(name)) {
                    throw resourceWithNameAlreadyExists(name);
                }
                resourceNames.add(name);
                return new ResourceDefinitionInfo(name, title, description, serverName, fun, asyncFun,
                        runOnVirtualThread, uri, mimeType, size, annotations);
            });
            if (newValue != null) {
                notifyConnections(McpMessageHandler.NOTIFICATIONS_RESOURCES_LIST_CHANGED);
            }
            return newValue;
        }
    }

}
