package io.quarkiverse.mcp.server.deployment;

import org.jboss.jandex.MethodInfo;

import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.InvokerInfo;
import io.quarkus.builder.item.MultiBuildItem;

final class FeatureMethodBuildItem extends MultiBuildItem {

    private final BeanInfo bean;
    private final InvokerInfo invoker;
    private final String name;
    private final String description;
    private final MethodInfo method;
    private final Feature feature;

    FeatureMethodBuildItem(BeanInfo bean, MethodInfo method, InvokerInfo invoker, String name, String description,
            Feature feature) {
        this.bean = bean;
        this.method = method;
        this.invoker = invoker;
        this.name = name;
        this.description = description;
        this.feature = feature;
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

    Feature getFeature() {
        return feature;
    }

    boolean isTool() {
        return feature == Feature.TOOL;
    }

    boolean isPrompt() {
        return feature == Feature.PROMPT;
    }

    enum Feature {
        PROMPT,
        TOOL
    }

}