package io.quarkiverse.mcp.server.deployment;

import java.util.Objects;

import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.Content.Annotations;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.runtime.Feature;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.InvokerInfo;
import io.quarkus.builder.item.MultiBuildItem;

final class FeatureMethodBuildItem extends MultiBuildItem {

    private final Feature feature;

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

    // Tool-only
    private final ToolManager.ToolAnnotations toolAnnotations;
    private final boolean structuredContent;
    private final Type outputSchemaFrom;
    private final Type outputSchemaGenerator;
    private final Type inputSchemaGenerator;

    // Server config name
    private final String server;

    FeatureMethodBuildItem(BeanInfo bean, MethodInfo method, InvokerInfo invoker, String name, String title, String description,
            String uri, String mimeType, int size, Feature feature, ToolManager.ToolAnnotations toolAnnotations,
            String server, boolean structuredContent, Type outputSchemaFrom, Type outputSchemaGenerator,
            Type inputSchemaGenerator,
            Content.Annotations resourceAnnotations) {
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
        this.server = server;
        this.structuredContent = structuredContent;
        this.outputSchemaFrom = outputSchemaFrom;
        this.outputSchemaGenerator = outputSchemaGenerator;
        this.inputSchemaGenerator = inputSchemaGenerator;
        this.resourceAnnotations = resourceAnnotations;
    }

    BeanInfo getBean() {
        return bean;
    }

    MethodInfo getMethod() {
        return method;
    }

    InvokerInfo getInvoker() {
        return invoker;
    }

    String getName() {
        return name;
    }

    String getTitle() {
        return title;
    }

    String getDescription() {
        return description;
    }

    String getUri() {
        return uri;
    }

    String getMimeType() {
        return mimeType;
    }

    int getSize() {
        return size;
    }

    Feature getFeature() {
        return feature;
    }

    ToolManager.ToolAnnotations getToolAnnotations() {
        return toolAnnotations;
    }

    String getServer() {
        return server;
    }

    boolean isStructuredContent() {
        return structuredContent;
    }

    Type getOutputSchemaFrom() {
        return outputSchemaFrom;
    }

    Type getOutputSchemaGenerator() {
        return outputSchemaGenerator;
    }

    Type getInputSchemaGenerator() {
        return inputSchemaGenerator;
    }

    Annotations getResourceAnnotations() {
        return resourceAnnotations;
    }

    boolean isTool() {
        return feature == Feature.TOOL;
    }

    boolean isPrompt() {
        return feature == Feature.PROMPT;
    }

    boolean isPromptComplete() {
        return feature == Feature.PROMPT_COMPLETE;
    }

    boolean isResource() {
        return feature == Feature.RESOURCE;
    }

    boolean isResourceTemplate() {
        return feature == Feature.RESOURCE_TEMPLATE;
    }

    boolean isResourceTemplateComplete() {
        return feature == Feature.RESOURCE_TEMPLATE_COMPLETE;
    }

    boolean isNotification() {
        return feature == Feature.NOTIFICATION;
    }

    @Override
    public String toString() {
        return "FeatureMethodBuildItem [name=" + name + ", method=" + method.declaringClass() + "#"
                + method.name()
                + "(), feature=" + feature + "]";
    }

}
