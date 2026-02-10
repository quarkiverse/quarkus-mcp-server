package io.quarkiverse.mcp.server.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.Content.Annotations;
import io.quarkiverse.mcp.server.FilterContext;
import io.quarkiverse.mcp.server.Icon;
import io.quarkiverse.mcp.server.IconsProvider;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.ResourceContentsEncoder.ResourceContentsData;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplateFilter;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.ResourceTemplateManager.ResourceTemplateInfo;
import io.quarkus.arc.All;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

@Singleton
public class ResourceTemplateManagerImpl extends FeatureManagerBase<ResourceResponse, ResourceTemplateInfo>
        implements ResourceTemplateManager {

    private static final Logger LOG = Logger.getLogger(ResourceTemplateManagerImpl.class);

    final ConcurrentMap<String, ResourceTemplateMetadata> templates;

    final List<ResourceTemplateFilter> filters;

    final Instance<IconsProvider> iconsProviders;

    ResourceTemplateManagerImpl(McpMetadata metadata,
            Vertx vertx,
            ObjectMapper mapper,
            ConnectionManager connectionManager,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation,
            ResponseHandlers responseHandlers,
            @All List<ResourceTemplateFilter> filters,
            @Any Instance<IconsProvider> iconsProviders) {
        super(vertx, mapper, connectionManager, currentIdentityAssociation, responseHandlers);
        this.templates = new ConcurrentHashMap<>();
        for (FeatureMetadata<ResourceResponse> fm : metadata.resourceTemplates()) {
            this.templates.put(fm.info().name(), new ResourceTemplateMetadata(createMatcherFromUriTemplate(fm.info().uri()),
                    new ResourceTemplateMethod(fm, iconsProviders)));
        }
        this.filters = filters;
        this.iconsProviders = iconsProviders;
    }

    @Override
    Stream<ResourceTemplateInfo> infos() {
        return templates.values().stream().map(ResourceTemplateMetadata::info);
    }

    @Override
    Stream<ResourceTemplateInfo> filter(Stream<ResourceTemplateInfo> infos, FilterContext filterContext) {
        return infos.filter(rt -> test(rt, filterContext));
    }

    @Override
    protected McpMethod mcpListMethod() {
        return McpMethod.RESOURCE_TEMPLATES_LIST;
    }

    @Override
    public ResourceTemplateInfo getResourceTemplate(String name) {
        ResourceTemplateMetadata metadata = templates.get(Objects.requireNonNull(name));
        return metadata != null ? metadata.info() : null;
    }

    @Override
    public ResourceTemplateDefinition newResourceTemplate(String name) {
        if (templates.containsKey(name)) {
            throw resourceTemplateWithNameAlreadyExists(name);
        }
        return new ResourceTemplateDefinitionImpl(name);
    }

    @Override
    public ResourceTemplateInfo removeResourceTemplate(String name) {
        AtomicReference<ResourceTemplateInfo> removed = new AtomicReference<>();
        templates.computeIfPresent(name, (key, value) -> {
            if (!value.info().isMethod()) {
                removed.set(value.info);
                return null;
            }
            return value;
        });
        return removed.get();
    }

    private VariableMatcher getVariableMatcher(String name) {
        return templates.get(name).variableMatcher();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected FeatureInvoker<ResourceResponse> getInvoker(String id, McpRequest mcpRequest, JsonObject message) {
        // This method is used by ResourceManager during "resources/read" - the id is a URI
        // We need to iterate over all templates and find the matching URI template
        ResourceTemplateInfo found = findMatching(id);
        if (found instanceof FeatureInvoker fi
                && matchesServer(found, mcpRequest)
                && test(found, FilterContextImpl.of(McpMethod.RESOURCES_READ, message, mcpRequest))) {
            return fi;
        }
        return null;
    }

    public ResourceTemplateInfo findMatching(String uri) {
        List<ResourceTemplateInfo> matching = new ArrayList<>();
        for (ResourceTemplateMetadata t : templates.values()) {
            if (t.variableMatcher().matches(uri)) {
                matching.add(t.info());
            }
        }
        if (matching.isEmpty()) {
            return null;
        } else if (matching.size() > 1) {
            throw new McpException("Multiple resource templates match uri %s [%s]".formatted(uri,
                    matching.stream().map(ResourceTemplateInfo::name).toList()), JsonRpcErrorCodes.INTERNAL_ERROR);
        } else {
            return matching.get(0);
        }

    }

    private boolean test(ResourceTemplateInfo resourceTemplate, FilterContext filterContext) {
        if (filters.isEmpty()) {
            return true;
        }
        for (ResourceTemplateFilter filter : filters) {
            try {
                if (!filter.test(resourceTemplate, filterContext)) {
                    return false;
                }
            } catch (RuntimeException e) {
                LOG.errorf(e, "Unable to apply filter: %s", filter);
            }
        }
        return true;
    }

    record ResourceTemplateMetadata(VariableMatcher variableMatcher, ResourceTemplateInfo info) {
    }

    IllegalArgumentException resourceTemplateWithNameAlreadyExists(String name) {
        return new IllegalArgumentException("A resource template with name [" + name + "] already exits");
    }

    @Override
    protected McpException notFound(String id) {
        return new McpException("Resource not found: " + id, JsonRpcErrorCodes.RESOURCE_NOT_FOUND);
    }

    @Override
    protected Object[] prepareArguments(FeatureMetadata<?> metadata, ArgumentProviders argProviders) throws McpException {
        // Use variable matching to extract method arguments
        Map<String, Object> matchedVariables = getVariableMatcher(metadata.info().name())
                .matchVariables(argProviders.uri()).entrySet().stream()
                .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().toString()));
        argProviders = new ArgumentProviders(argProviders.rawMessage(),
                matchedVariables, argProviders.connection(), argProviders.requestId(), argProviders.uri(),
                argProviders.sender(), argProviders.progressToken(), responseHandlers, argProviders.serverName());
        return super.prepareArguments(metadata, argProviders);
    }

    public static VariableMatcher createMatcherFromUriTemplate(String uriTemplate) {
        // Find variables
        List<String> variables = new ArrayList<>();
        Matcher m = Pattern.compile("\\{(\\w+)\\}").matcher(uriTemplate);
        StringBuilder uriRegex = new StringBuilder();
        while (m.find()) {
            variables.add(m.group(1));
            m.appendReplacement(uriRegex, "([^/]+)");
        }
        m.appendTail(uriRegex);
        return new VariableMatcher(Pattern.compile(uriRegex.toString()), variables);
    }

    @Override
    protected Object wrapResult(FeatureInvoker<?> invoker, Object ret, FeatureMetadata<?> metadata,
            ArgumentProviders argProviders) {
        if (ret == null) {
            throw notFound(argProviders.uri());
        }
        if (invoker instanceof ResourceTemplateMethod m
                && metadata.resultMapper() instanceof EncoderMapper) {
            // We need to wrap the returned value with ResourceContentsData
            // Supported variants are Uni<X>, List<X>, Uni<List<X>
            if (ret instanceof Uni<?> uni) {
                return uni.map(i -> {
                    if (i instanceof List<?> list) {
                        return list.stream().map(
                                e -> new ResourceContentsData<>(new RequestUri(argProviders.uri()), e, m))
                                .toList();
                    }
                    return new ResourceContentsData<>(new RequestUri(argProviders.uri()), i, m);
                });
            } else if (ret instanceof List<?> list) {
                return list.stream()
                        .map(e -> new ResourceContentsData<>(new RequestUri(argProviders.uri()), e, m))
                        .toList();
            }
            return new ResourceContentsData<>(new RequestUri(argProviders.uri()), ret, m);
        }
        return super.wrapResult(invoker, ret, metadata, argProviders);
    }

    public record VariableMatcher(Pattern pattern, List<String> variables) {

        boolean matches(String uri) {
            return pattern.matcher(uri).matches();
        }

        Map<String, String> matchVariables(String uri) {
            Map<String, String> ret = new HashMap<>();
            Matcher m = pattern.matcher(uri);
            if (m.matches()) {
                for (int i = 0; i < m.groupCount(); i++) {
                    ret.put(variables.get(i), m.group(i + 1));
                }
            }
            return ret;
        }

    }

    class ResourceTemplateMethod extends FeatureMetadataInvoker<ResourceResponse>
            implements ResourceTemplateManager.ResourceTemplateInfo {

        private ResourceTemplateMethod(FeatureMetadata<ResourceResponse> metadata, Instance<IconsProvider> iconsProviders) {
            super(metadata, iconsProviders);
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
        public String uriTemplate() {
            return metadata.info().uri();
        }

        @Override
        public String mimeType() {
            return metadata.info().mimeType();
        }

        @Override
        public Optional<Annotations> annotations() {
            return Optional.ofNullable(metadata.info().resourceAnnotations());
        }

        @Override
        public Map<MetaKey, Object> metadata() {
            return metadata.info().metadata().entrySet()
                    .stream()
                    .collect(Collectors.toUnmodifiableMap(e -> MetaKey.from(e.getKey()), e -> Json.decodeValue(e.getValue())));
        }

        @Override
        public boolean isMethod() {
            return true;
        }

        @Override
        public JsonObject asJson() {
            JsonObject resourceTemplate = metadata.asJson();
            if (iconsProvider != null) {
                try {
                    List<Icon> icons = iconsProvider.get(this);
                    resourceTemplate.put("icons", icons);
                } catch (Exception e) {
                    LOG.errorf(e, "Unable to get icons for %s", name());
                }
            }
            return resourceTemplate;
        }

    }

    class ResourceTemplateDefinitionInfo
            extends FeatureManagerBase.FeatureDefinitionInfoBase<ResourceTemplateArguments, ResourceResponse>
            implements ResourceTemplateManager.ResourceTemplateInfo {

        private final String title;
        private final String uriTemplate;
        private final String mimeType;
        private final Content.Annotations annotations;
        private final Map<MetaKey, Object> metadata;

        private ResourceTemplateDefinitionInfo(String name, String title, String description, String serverName,
                Function<ResourceTemplateArguments, ResourceResponse> fun,
                Function<ResourceTemplateArguments, Uni<ResourceResponse>> asyncFun, boolean runOnVirtualThread, String uri,
                String mimeType, Content.Annotations annotations, Map<MetaKey, Object> metadata, List<Icon> icons) {
            super(name, description, serverName, fun, asyncFun, runOnVirtualThread, icons);
            this.title = title;
            this.uriTemplate = uri;
            this.mimeType = mimeType;
            this.annotations = annotations;
            this.metadata = Map.copyOf(metadata);
        }

        @Override
        public String title() {
            return title;
        }

        @Override
        public String uriTemplate() {
            return uriTemplate;
        }

        @Override
        public String mimeType() {
            return mimeType;
        }

        @Override
        public Optional<Annotations> annotations() {
            return Optional.ofNullable(annotations);
        }

        @Override
        public Map<MetaKey, Object> metadata() {
            return metadata;
        }

        @Override
        public JsonObject asJson() {
            JsonObject ret = new JsonObject().put("name", name())
                    .put("description", description())
                    .put("uriTemplate", uriTemplate())
                    .put("mimeType", mimeType());
            if (title != null) {
                ret.put("title", title);
            }
            if (annotations != null) {
                ret.put("annotations", annotations);
            }
            if (!metadata.isEmpty()) {
                JsonObject meta = new JsonObject();
                for (Map.Entry<MetaKey, Object> e : metadata.entrySet()) {
                    meta.put(e.getKey().toString(), e.getValue());
                }
                ret.put("_meta", meta);
            }
            if (icons != null) {
                ret.put("icons", icons);
            }
            return ret;
        }

        @Override
        protected ResourceTemplateArguments createArguments(ArgumentProviders argumentProviders) {
            // Use variable matching to extract method arguments
            Map<String, String> matchedVariables = getVariableMatcher(name)
                    .matchVariables(argumentProviders.uri());
            return new ResourceTemplateArgumentsImpl(argumentProviders,
                    matchedVariables,
                    new RequestUri(argumentProviders.uri()),
                    log(Feature.RESOURCE_TEMPLATE.toString().toLowerCase() + ":" + name, name, argumentProviders));
        }

    }

    static class ResourceTemplateArgumentsImpl extends AbstractRequestFeatureArguments implements ResourceTemplateArguments {

        private final Map<String, String> args;
        private final RequestUri requestUri;
        private final McpLog log;

        ResourceTemplateArgumentsImpl(ArgumentProviders argProviders, Map<String, String> args, RequestUri requestUri,
                McpLog log) {
            super(argProviders);
            this.args = Map.copyOf(args);
            this.requestUri = requestUri;
            this.log = log;
        }

        @Override
        public McpLog log() {
            return log;
        }

        @Override
        public Map<String, String> args() {
            return args;
        }

        @Override
        public RequestUri requestUri() {
            return requestUri;
        }

    }

    class ResourceTemplateDefinitionImpl extends
            FeatureManagerBase.FeatureDefinitionBase<ResourceTemplateInfo, ResourceTemplateArguments, ResourceResponse, ResourceTemplateDefinitionImpl>
            implements ResourceTemplateManager.ResourceTemplateDefinition {

        private String title;
        private String uriTemplate;
        private String mimeType;
        private Annotations annotations;
        private Map<MetaKey, Object> metadata = Map.of();

        ResourceTemplateDefinitionImpl(String name) {
            super(name);
        }

        @Override
        public ResourceTemplateDefinition setTitle(String title) {
            this.title = title;
            return this;
        }

        @Override
        public ResourceTemplateDefinition setUriTemplate(String uriTemplate) {
            this.uriTemplate = Objects.requireNonNull(uriTemplate);
            return this;
        }

        @Override
        public ResourceTemplateDefinition setMimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        @Override
        public ResourceTemplateDefinition setAnnotations(Annotations annotations) {
            this.annotations = annotations;
            return this;
        }

        @Override
        public ResourceTemplateDefinition setMetadata(Map<MetaKey, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        @Override
        public ResourceTemplateInfo register() {
            validate();
            if (uriTemplate == null) {
                throw new IllegalStateException("uriTemplate must be set");
            }
            ResourceTemplateDefinitionInfo ret = new ResourceTemplateDefinitionInfo(name, title, description, serverName,
                    fun, asyncFun, runOnVirtualThread, uriTemplate, mimeType, annotations, metadata, icons);
            VariableMatcher variableMatcher = createMatcherFromUriTemplate(uriTemplate);
            ResourceTemplateMetadata existing = templates.putIfAbsent(name, new ResourceTemplateMetadata(variableMatcher, ret));
            if (existing != null) {
                throw resourceTemplateWithNameAlreadyExists(name);
            }
            return ret;
        }
    }

}
