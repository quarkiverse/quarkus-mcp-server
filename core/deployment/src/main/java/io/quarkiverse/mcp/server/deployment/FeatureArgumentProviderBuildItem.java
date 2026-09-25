package io.quarkiverse.mcp.server.deployment;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.jboss.jandex.DotName;

import io.quarkiverse.mcp.server.Feature;
import io.quarkiverse.mcp.server.FeatureArgumentProvider;
import io.quarkus.builder.item.MultiBuildItem;

/**
 * Registers a custom injectable parameter type of a feature method.
 * <p>
 * An MCP extension produces this build item to make a specific parameter type injectable into a feature method (such as a
 * {@link io.quarkiverse.mcp.server.Tool}, {@link io.quarkiverse.mcp.server.Prompt} or
 * {@link io.quarkiverse.mcp.server.Resource} method). Whenever a feature method declares a parameter of the registered type,
 * the value is supplied by the associated {@link FeatureArgumentProvider} instead of being deserialized from the request
 * arguments.
 *
 * @see FeatureArgumentProvider
 */
public final class FeatureArgumentProviderBuildItem extends MultiBuildItem {

    private final DotName parameterType;

    private final String providerClassName;

    private final Set<Feature> features;

    public FeatureArgumentProviderBuildItem(DotName parameterType, String providerClassName) {
        this(parameterType, providerClassName, EnumSet.allOf(Feature.class));
    }

    /**
     * @param parameterType the type of the parameter that should be injected (must not be {@code null})
     * @param providerClassName the fully qualified name of the {@link FeatureArgumentProvider} implementation that supplies the
     *        value (must not be {@code null})
     * @param features the features the provider should be used for (must not be {@code null} or empty)
     */
    public FeatureArgumentProviderBuildItem(DotName parameterType, String providerClassName, Set<Feature> features) {
        this.parameterType = Objects.requireNonNull(parameterType, "parameterType must not be null");
        this.providerClassName = Objects.requireNonNull(providerClassName, "providerClassName must not be null");
        if (FeatureArguments.isBuiltinType(parameterType)) {
            throw new IllegalArgumentException(
                    "A built-in injectable type cannot be registered as a custom argument type: " + parameterType);
        }
        Objects.requireNonNull(features, "Features must not be null");
        if (features.isEmpty()) {
            throw new IllegalArgumentException("Features must not be empty");
        }
        this.features = Set.copyOf(features);
    }

    public DotName getParameterType() {
        return parameterType;
    }

    public String getProviderClassName() {
        return providerClassName;
    }

    /**
     * @return an immutable set of the features the provider should be used for
     */
    public Set<Feature> getFeatures() {
        return features;
    }

    /**
     * @param feature
     * @return {@code true} if the provider should be used for the given feature
     */
    public boolean appliesTo(Feature feature) {
        return features.contains(feature);
    }

}
