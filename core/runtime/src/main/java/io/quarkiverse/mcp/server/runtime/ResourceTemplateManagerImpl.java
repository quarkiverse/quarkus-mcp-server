package io.quarkiverse.mcp.server.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.ResourceContentsEncoder;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplateFilter;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.ResourceTemplateManager.ResourceTemplateInfo;
import io.quarkus.arc.All;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

@Singleton
public class ResourceTemplateManagerImpl extends FeatureManagerBase<ResourceResponse, ResourceTemplateInfo>
        implements ResourceTemplateManager {

    private static final Logger LOG = Logger.getLogger(ResourceTemplateManagerImpl.class);

    final ConcurrentMap<String, ResourceTemplateMetadata> templates;

    final List<ResourceTemplateFilter> filters;

    ResourceTemplateManagerImpl(McpMetadata metadata,
            Vertx vertx,
            ObjectMapper mapper,
            ConnectionManager connectionManager,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation,
            ResponseHandlers responseHandlers,
            @All List<ResourceTemplateFilter> filters) {
        super(vertx, mapper, connectionManager, currentIdentityAssociation, responseHandlers);
        this.templates = new ConcurrentHashMap<>();
        for (FeatureMetadata<ResourceResponse> fm : metadata.resourceTemplates()) {
            this.templates.put(fm.info().name(), new ResourceTemplateMetadata(createMatcherFromUriTemplate(fm.info().uri()),
                    new ResourceTemplateMethod(fm)));
        }
        this.filters = filters;
    }

    @Override
    Stream<ResourceTemplateInfo> infos() {
        return templates.values().stream().map(ResourceTemplateMetadata::info);
    }

    @Override
    Stream<ResourceTemplateInfo> filter(Stream<ResourceTemplateInfo> infos, McpConnection connection) {
        return infos.filter(rt -> test(rt, connection));
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
    protected FeatureInvoker<ResourceResponse> getInvoker(String id, McpRequest mcpRequest) {
        // This method is used by ResourceManager during "resources/read" - the id is a URI
        // We need to iterate over all templates and find the matching URI template
        ResourceTemplateInfo found = findMatching(id);
        if (found instanceof FeatureInvoker fi
                && matches(found, mcpRequest)
                && test(found, mcpRequest.connection())) {
            return fi;
        }
        return null;
    }

    public ResourceTemplateInfo findMatching(String uri) {
        for (ResourceTemplateMetadata t : templates.values()) {
            if (t.variableMatcher().matches(uri)) {
                return t.info();
            }
        }
        return null;
    }

    private boolean test(ResourceTemplateInfo resourceTemplate, McpConnection connection) {
        if (filters.isEmpty()) {
            return true;
        }
        for (ResourceTemplateFilter filter : filters) {
            try {
                if (!filter.test(resourceTemplate, connection)) {
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
        return new McpException("Invalid resource uri: " + id, JsonRPC.RESOURCE_NOT_FOUND);
    }

    @Override
    protected Object[] prepareArguments(FeatureMetadata<?> metadata, ArgumentProviders argProviders) throws McpException {
        // Use variable matching to extract method arguments
        Map<String, Object> matchedVariables = getVariableMatcher(metadata.info().name())
                .matchVariables(argProviders.uri()).entrySet().stream()
                .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().toString()));
        argProviders = new ArgumentProviders(
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

        private ResourceTemplateMethod(FeatureMetadata<ResourceResponse> metadata) {
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
        public boolean isMethod() {
            return true;
        }

        @Override
        public JsonObject asJson() {
            return metadata.asJson();
        }

    }

    class ResourceTemplateDefinitionInfo
            extends FeatureManagerBase.FeatureDefinitionInfoBase<ResourceTemplateArguments, ResourceResponse>
            implements ResourceTemplateManager.ResourceTemplateInfo {

        private final String uriTemplate;
        private final String mimeType;

        private ResourceTemplateDefinitionInfo(String name, String description, String serverName,
                Function<ResourceTemplateArguments, ResourceResponse> fun,
                Function<ResourceTemplateArguments, Uni<ResourceResponse>> asyncFun, boolean runOnVirtualThread, String uri,
                String mimeType) {
            super(name, description, serverName, fun, asyncFun, runOnVirtualThread);
            this.uriTemplate = uri;
            this.mimeType = mimeType;
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
        public JsonObject asJson() {
            return new JsonObject().put("name", name())
                    .put("description", description())
                    .put("uriTemplate", uriTemplate())
                    .put("mimeType", mimeType());
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

        private String uriTemplate;
        private String mimeType;

        ResourceTemplateDefinitionImpl(String name) {
            super(name);
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
        public ResourceTemplateInfo register() {
            validate();
            ResourceTemplateDefinitionInfo ret = new ResourceTemplateDefinitionInfo(name, description, serverName,
                    fun, asyncFun,
                    runOnVirtualThread, uriTemplate, mimeType);
            VariableMatcher variableMatcher = createMatcherFromUriTemplate(uriTemplate);
            ResourceTemplateMetadata existing = templates.putIfAbsent(name, new ResourceTemplateMetadata(variableMatcher, ret));
            if (existing != null) {
                throw resourceTemplateWithNameAlreadyExists(name);
            }
            return ret;
        }
    }

}
