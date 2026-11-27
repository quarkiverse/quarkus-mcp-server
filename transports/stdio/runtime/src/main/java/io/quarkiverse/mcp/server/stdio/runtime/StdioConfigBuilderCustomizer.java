package io.quarkiverse.mcp.server.stdio.runtime;

import java.util.OptionalInt;

import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigSourceInterceptorFactory;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.Priorities;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilderCustomizer;

/**
 * Redirect console logging to {@code System#err} instead of {@code System#out}, unless the stdio transport is disabled, i.e.
 * {@code quarkus.mcp.server.stdio.enabled=false}.
 * <p>
 * Originally, we tried to use the {@code RunTimeConfigurationDefaultBuildItem}. However, this build item has no effect unless a
 * Quarkus version with <a href="https://github.com/quarkusio/quarkus/pull/45431">a fix</a> is used. Moreover, a build-time
 * default cannot take the runtime value of {@code quarkus.mcp.server.stdio.enabled} into account.
 * <p>
 * We use an interceptor instead of a config source because the value depends on another config property. An interceptor can
 * see all config sources, including the ones created by a {@code ConfigSourceFactory}.
 */
public class StdioConfigBuilderCustomizer implements SmallRyeConfigBuilderCustomizer {

    @Override
    public void configBuilder(SmallRyeConfigBuilder builder) {
        builder.withInterceptorFactories(new ConfigSourceInterceptorFactory() {

            @Override
            public ConfigSourceInterceptor getInterceptor(ConfigSourceInterceptorContext context) {
                return new ConsoleStderrInterceptor();
            }

            @Override
            public OptionalInt getPriority() {
                // Must be higher than the priority of built-in interceptors so that profiles and expressions are resolved
                // when we call ConfigSourceInterceptorContext#proceed()
                return OptionalInt.of(Priorities.APPLICATION);
            }
        });
    }

    static class ConsoleStderrInterceptor implements ConfigSourceInterceptor {

        private static final long serialVersionUID = 1L;

        static final String CONSOLE_STDERR = "quarkus.log.console.stderr";
        static final String ENABLED = "quarkus.mcp.server.stdio.enabled";
        static final String SOURCE_NAME = "mcp-stdio-config-source";
        // Keep the low ordinal so that users can override the config property
        static final int ORDINAL = 50;

        @Override
        public ConfigValue getValue(ConfigSourceInterceptorContext context, String name) {
            ConfigValue value = context.proceed(name);
            if (!CONSOLE_STDERR.equals(name)) {
                return value;
            }
            if (value != null && value.getConfigSourceOrdinal() >= ORDINAL) {
                // Explicitly set by the user
                return value;
            }
            // The value is not set or it comes from a config source with a lower ordinal, e.g. the default value
            ConfigValue enabled = context.proceed(ENABLED);
            if (enabled != null && enabled.getValue() != null && !Boolean.parseBoolean(enabled.getValue().trim())) {
                // The stdio transport is disabled - do not redirect console logging
                // Note that we cannot simply return the original value because Quarkus records the value computed by this
                // interceptor at build time (the "Runtime Values" config source with ordinal 0); i.e. it would be "true" if
                // the stdio transport is only disabled at runtime
                return withValue(value, name, "false");
            }
            return withValue(value, name, "true");
        }

        private static ConfigValue withValue(ConfigValue value, String name, String newValue) {
            ConfigValue.ConfigValueBuilder builder = value != null ? value.from() : ConfigValue.builder().withName(name);
            return builder
                    .withValue(newValue)
                    .withRawValue(newValue)
                    .withConfigSourceName(SOURCE_NAME)
                    .withConfigSourceOrdinal(ORDINAL)
                    .withConfigSourcePosition(0)
                    .withLineNumber(-1)
                    .build();
        }

    }

}
