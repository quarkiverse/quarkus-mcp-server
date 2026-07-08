package io.quarkiverse.mcp.server.deployment;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import io.quarkiverse.mcp.server.CacheControl;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.Content.Annotations;
import io.quarkiverse.mcp.server.ExecutionModel;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.runtime.Feature;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.InvokerInfo;
import io.quarkus.builder.item.MultiBuildItem;

/**
 * Represents a CDI bean method annotated with one of the MCP feature annotations, such as
 * {@link io.quarkiverse.mcp.server.Tool @Tool}, {@link io.quarkiverse.mcp.server.Prompt @Prompt},
 * {@link io.quarkiverse.mcp.server.Resource @Resource}, etc.
 */
public final class FeatureMethodBuildItem extends MultiBuildItem {

    static final Comparator<FeatureMethodBuildItem> NAME_COMPARATOR = Comparator.comparing(fm -> fm.getName());

    private final Feature feature;
    private final ExecutionModel executionModel;

    // Invocation info
    private final BeanInfo bean;
    private final InvokerInfo invoker;
    private final MethodInfo method;

    // The name of the feature
    private final String name;
    private final String title;

    // Optional description
    private final String description;

    // Resource-only
    private final String uri;
    private final String mimeType;
    private final int size;
    private final Content.Annotations resourceAnnotations;
    private final CacheControl cacheControl;

    // Tool-only
    private final ToolManager.ToolAnnotations toolAnnotations;
    private final boolean structuredContent;
    private final Type outputSchemaFrom;
    private final Type outputSchemaGenerator;
    private final Type inputSchemaGenerator;

    private final List<DotName> inputGuardrails;
    private final List<DotName> outputGuardrails;

    private final DotName iconsProvider;

    // Server config names
    private final Set<String> servers;

    // meta key (prefix + name) -> json
    private final Map<String, String> metadata;

    FeatureMethodBuildItem(BeanInfo bean, MethodInfo method, InvokerInfo invoker, String name, String title, String description,
            String uri, String mimeType, int size, Feature feature, ToolManager.ToolAnnotations toolAnnotations,
            Set<String> servers, boolean structuredContent, Type outputSchemaFrom, Type outputSchemaGenerator,
            Type inputSchemaGenerator,
            Content.Annotations resourceAnnotations,
            CacheControl cacheControl,
            Map<String, String> metadata,
            List<DotName> inputGuardrails,
            List<DotName> outputGuardrails,
            ExecutionModel executionModel,
            DotName iconsProvider) {
        this.bean = Objects.requireNonNull(bean);
        this.method = Objects.requireNonNull(method);
        this.invoker = Objects.requireNonNull(invoker);
        this.feature = Objects.requireNonNull(feature);
        this.name = Objects.requireNonNull(name);
        this.title = title;
        this.description = description;
        this.uri = feature.requiresUri() ? Objects.requireNonNull(uri) : null;
        this.mimeType = mimeType;
        this.size = size;
        this.toolAnnotations = toolAnnotations;
        this.servers = servers;
        this.structuredContent = structuredContent;
        this.outputSchemaFrom = outputSchemaFrom;
        this.outputSchemaGenerator = outputSchemaGenerator;
        this.inputSchemaGenerator = inputSchemaGenerator;
        this.resourceAnnotations = resourceAnnotations;
        this.cacheControl = cacheControl;
        this.metadata = Objects.requireNonNull(metadata);
        this.inputGuardrails = inputGuardrails;
        this.outputGuardrails = outputGuardrails;
        this.executionModel = executionModel;
        this.iconsProvider = iconsProvider;
    }

    public BeanInfo getBean() {
        return bean;
    }

    public MethodInfo getMethod() {
        return method;
    }

    public InvokerInfo getInvoker() {
        return invoker;
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getUri() {
        return uri;
    }

    public String getMimeType() {
        return mimeType;
    }

    public int getSize() {
        return size;
    }

    public Feature getFeature() {
        return feature;
    }

    public ToolManager.ToolAnnotations getToolAnnotations() {
        return toolAnnotations;
    }

    public Set<String> getServers() {
        return Collections.unmodifiableSet(servers);
    }

    public boolean isStructuredContent() {
        return structuredContent;
    }

    public Type getOutputSchemaFrom() {
        return outputSchemaFrom;
    }

    public Type getOutputSchemaGenerator() {
        return outputSchemaGenerator;
    }

    public Type getInputSchemaGenerator() {
        return inputSchemaGenerator;
    }

    public Annotations getResourceAnnotations() {
        return resourceAnnotations;
    }

    public CacheControl getCacheControl() {
        return cacheControl;
    }

    public Map<String, String> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    public List<DotName> getInputGuardrails() {
        return Collections.unmodifiableList(inputGuardrails);
    }

    public List<DotName> getOutputGuardrails() {
        return Collections.unmodifiableList(outputGuardrails);
    }

    public ExecutionModel getExecutionModel() {
        return executionModel;
    }

    public DotName getIconsProvider() {
        return iconsProvider;
    }

    public boolean isTool() {
        return feature == Feature.TOOL;
    }

    public boolean isPrompt() {
        return feature == Feature.PROMPT;
    }

    public boolean isPromptComplete() {
        return feature == Feature.PROMPT_COMPLETE;
    }

    public boolean isResource() {
        return feature == Feature.RESOURCE;
    }

    public boolean isResourceTemplate() {
        return feature == Feature.RESOURCE_TEMPLATE;
    }

    public boolean isResourceTemplateComplete() {
        return feature == Feature.RESOURCE_TEMPLATE_COMPLETE;
    }

    public boolean isNotification() {
        return feature == Feature.NOTIFICATION;
    }

    @Override
    public String toString() {
        return "FeatureMethodBuildItem [name=" + name + ", method=" + method.declaringClass() + "#"
                + method.name()
                + "(), feature=" + feature + "]";
    }

}
