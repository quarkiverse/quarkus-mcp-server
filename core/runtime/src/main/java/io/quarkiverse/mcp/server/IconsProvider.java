package io.quarkiverse.mcp.server;

import java.util.List;

import io.quarkiverse.mcp.server.FeatureManager.FeatureInfo;

/**
 * Used to provide icons for a specific feature.
 * <p>
 * Implementations must be CDI beans, or declare a public no-args constructor. In case of CDI, there must be exactly one bean
 * that has the implementation class in its set of bean types, otherwise the build fails. Qualifiers are ignored. Furthermore,
 * the context of the bean must be active during execution.
 * <p>
 * Execution of a provider must not block the caller thread. If you intend to use a {@code data:} URI with base64-encoded image
 * data then we recommend to load it separately and cache the data in memory.
 *
 * @see Icons
 */
public interface IconsProvider {

    /**
     * @param feature
     * @return the list of icons
     */
    List<Icon> get(FeatureInfo feature);

}
