package io.quarkiverse.mcp.server.stdio.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.stdio.runtime.StdioConfigBuilderCustomizer;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

public class ConsoleStderrConfigTest {

    private static final String CONSOLE_STDERR = "quarkus.log.console.stderr";
    private static final String ENABLED = "quarkus.mcp.server.stdio.enabled";

    @Test
    public void testStdioEnabledByDefault() {
        SmallRyeConfig config = buildConfig(Map.of());
        assertStderr(config, true);
        assertEquals("mcp-stdio-config-source", config.getConfigValue(CONSOLE_STDERR).getConfigSourceName());
    }

    @Test
    public void testStdioExplicitlyEnabled() {
        assertStderr(buildConfig(Map.of(ENABLED, "true")), true);
    }

    @Test
    public void testStdioDisabled() {
        assertStderr(buildConfig(Map.of(ENABLED, "false")), false);
    }

    @Test
    public void testStdioDisabledInActiveProfile() {
        assertStderr(buildConfig(Map.of("%dev." + ENABLED, "false"), b -> b.withProfile("dev")), false);
        // Inactive profile
        assertStderr(buildConfig(Map.of("%prod." + ENABLED, "false"), b -> b.withProfile("dev")), true);
    }

    @Test
    public void testStdioDisabledViaExpression() {
        assertStderr(buildConfig(Map.of(ENABLED, "${stdio.on:false}")), false);
        assertStderr(buildConfig(Map.of(ENABLED, "${stdio.on:false}", "stdio.on", "true")), true);
    }

    @Test
    public void testStdioDisabledInFactorySource() {
        assertStderr(buildConfig(Map.of(), b -> b.withSources(
                (ConfigSourceFactory) context -> List
                        .of(new PropertiesConfigSource(Map.of(ENABLED, "false"), "factory", 100)))),
                false);
    }

    @Test
    public void testStdioDisabledAtRuntimeOnly() {
        // Quarkus records the value computed at build time in the "Runtime Values" config source with ordinal 0
        assertStderr(buildConfig(Map.of(ENABLED, "false"),
                b -> b.withSources(new PropertiesConfigSource(Map.of(CONSOLE_STDERR, "true"), "Runtime Values", 0))),
                false);
    }

    @Test
    public void testUserOverride() {
        // Stdio enabled but the user wants to log to stdout
        assertStderr(buildConfig(Map.of(CONSOLE_STDERR, "false")), false);
        // Stdio disabled but the user wants to log to stderr
        assertStderr(buildConfig(Map.of(ENABLED, "false", CONSOLE_STDERR, "true")), true);
    }

    private static void assertStderr(SmallRyeConfig config, boolean expected) {
        ConfigValue value = config.getConfigValue(CONSOLE_STDERR);
        assertEquals(String.valueOf(expected), value.getValue(), value.toString());
        assertEquals(expected, config.getValue(CONSOLE_STDERR, Boolean.class));
    }

    private static SmallRyeConfig buildConfig(Map<String, String> properties) {
        return buildConfig(properties, b -> {
        });
    }

    private static SmallRyeConfig buildConfig(Map<String, String> properties, Consumer<SmallRyeConfigBuilder> customizer) {
        SmallRyeConfigBuilder builder = new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                // Emulate the default value of the logging config mapping
                .withDefaultValue(CONSOLE_STDERR, "false")
                .withSources(new PropertiesConfigSource(properties, "test", 250));
        new StdioConfigBuilderCustomizer().configBuilder(builder);
        customizer.accept(builder);
        return builder.build();
    }

}
