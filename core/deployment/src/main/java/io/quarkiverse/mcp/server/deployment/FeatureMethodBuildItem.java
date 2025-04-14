package io.quarkiverse.mcp.server.deployment;

import java.util.Objects;

import org.jboss.jandex.MethodInfo;

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

    // Optional description
    private final String description;

    // Resource-only
    private final String uri;
    private final String mimeType;

    FeatureMethodBuildItem(BeanInfo bean, MethodInfo method, InvokerInfo invoker, String name, String description, String uri,
            String mimeType, Feature feature) {
        this.bean = Objects.requireNonNull(bean);
        this.method = Objects.requireNonNull(method);
        this.invoker = Objects.requireNonNull(invoker);
        this.feature = Objects.requireNonNull(feature);
        this.name = Objects.requireNonNull(name);
        this.description = description;
        this.uri = feature.requiresUri() ? Objects.requireNonNull(uri) : null;
        this.mimeType = mimeType;
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

    String getDescription() {
        return description;
    }

    String getUri() {
        return uri;
    }

    String getMimeType() {
        return mimeType;
    }

    Feature getFeature() {
        return feature;
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

    boolean isInit() {
        return feature == Feature.NOTIFICATION;
    }

    @Override
    public String toString() {
        return "FeatureMethodBuildItem [name=" + name + ", method=" + method.declaringClass() + "#"
                + method.name()
                + "(), feature=" + feature + "]";
    }

}
