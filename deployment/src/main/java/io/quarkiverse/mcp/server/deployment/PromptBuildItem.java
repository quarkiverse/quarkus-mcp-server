package io.quarkiverse.mcp.server.deployment;

import org.jboss.jandex.MethodInfo;

import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.InvokerInfo;
import io.quarkus.builder.item.MultiBuildItem;

public final class PromptBuildItem extends MultiBuildItem {

    private final BeanInfo bean;
    private final InvokerInfo invoker;
    private final String name;
    private final String description;
    private final MethodInfo method;

    PromptBuildItem(BeanInfo bean, MethodInfo method, InvokerInfo invoker, String name, String description) {
        this.bean = bean;
        this.method = method;
        this.invoker = invoker;
        this.name = name;
        this.description = description;
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

    public String getDescription() {
        return description;
    }

}
