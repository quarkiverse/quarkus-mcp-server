package io.quarkiverse.mcp.server.deployment;

import org.jboss.jandex.Type;

import io.quarkus.builder.item.MultiBuildItem;

final class DefaultValueConverterBuildItem extends MultiBuildItem {

    private final int priority;

    private final String className;

    private final Type argumentType;

    DefaultValueConverterBuildItem(int priority, String className, Type argumentType) {
        this.priority = priority;
        this.className = className;
        this.argumentType = argumentType;
    }

    public int getPriority() {
        return priority;
    }

    public String getClassName() {
        return className;
    }

    public Type getArgumentType() {
        return argumentType;
    }

}
