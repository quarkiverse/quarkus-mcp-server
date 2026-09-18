package io.quarkiverse.mcp.server.deployment;

import java.util.Set;

import org.jboss.jandex.DotName;

import io.quarkiverse.mcp.server.runtime.Feature;
import io.quarkus.builder.item.MultiBuildItem;

/**
 * Registers a custom parameter type of feature methods. A parameter of this type is not treated as a serialized argument
 * (e.g. a tool argument) but as a provider supplied by an MCP extension at runtime; see
 * {@link io.quarkiverse.mcp.server.runtime.ArgumentProviders#customProvider(Class)}.
 */
public final class CustomArgumentTypeBuildItem extends MultiBuildItem {

    private final DotName type;
    private final Set<Feature> features;

    /**
     * @param type the parameter type
     * @param features the features whose methods may declare a parameter of this type
     */
    public CustomArgumentTypeBuildItem(DotName type, Set<Feature> features) {
        this.type = type;
        this.features = Set.copyOf(features);
    }

    public DotName getType() {
        return type;
    }

    public Set<Feature> getFeatures() {
        return features;
    }

}
