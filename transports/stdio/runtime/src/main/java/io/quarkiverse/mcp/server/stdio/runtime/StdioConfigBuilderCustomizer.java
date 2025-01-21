package io.quarkiverse.mcp.server.stdio.runtime;

import java.util.Map;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilderCustomizer;

/**
 * Redirect console logging to {@code System#err} instead of {@code System#out}.
 * <p>
 * Originally, we tried to use the {@code RunTimeConfigurationDefaultBuildItem}. However, this build item has no effect unless a
 * Quarkus version with <a href="https://github.com/quarkusio/quarkus/pull/45431">a fix</a> is used.
 */
public class StdioConfigBuilderCustomizer implements SmallRyeConfigBuilderCustomizer {

    @Override
    public void configBuilder(SmallRyeConfigBuilder builder) {
        builder.withSources(
                new PropertiesConfigSource(
                        Map.of("quarkus.log.console.stderr", "true"),
                        "mcp-stdio-config-source",
                        // Keep the low ordinal so that users can override the config property
                        50));
    }

}
