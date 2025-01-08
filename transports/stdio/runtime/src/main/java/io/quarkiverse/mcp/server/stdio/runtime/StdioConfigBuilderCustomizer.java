package io.quarkiverse.mcp.server.stdio.runtime;

import java.util.Map;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilderCustomizer;

/**
 * Always disable console logging.
 * <p>
 * Originally, we tried to use the {@code RunTimeConfigurationDefaultBuildItem}. However, this build item has no effect unless a
 * Quarkus version with <a href="https://github.com/quarkusio/quarkus/pull/45431">a fix</a> is used.
 */
public class StdioConfigBuilderCustomizer implements SmallRyeConfigBuilderCustomizer {

    @Override
    public void configBuilder(SmallRyeConfigBuilder builder) {
        builder.withSources(
                new PropertiesConfigSource(
                        Map.of("quarkus.log.console.enable", "false"),
                        "mcp-stdio-config-source", 500));
    }

}
