package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationOption;

import io.quarkiverse.mcp.server.runtime.config.McpServerSchemaGeneratorJakartaValidationRuntimeConfig;

class SchemaGeneratorConfigCustomizerJakartaValidationTest {

    @Test
    void customize_disabled() {
        // Given
        var config = Config.createDisableConfig();
        var customizer = new ConfigCustomizerSpy(config);

        // When
        customizer.customize(new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .without(Option.SCHEMA_VERSION_INDICATOR));

        // Then
        assertFalse(customizer.isModuleCreated);
    }

    @ParameterizedTest
    @MethodSource("provideConfigOptions")
    void customize_enabled_with_options(Consumer<Config> configModifier,
            JakartaValidationOption expectedJakartaValidationOption) {
        // Given
        var config = Config.createEnabledConfig(configModifier);
        var customizer = new ConfigCustomizerSpy(config);

        // When
        customizer.customize(new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .without(Option.SCHEMA_VERSION_INDICATOR));

        // Then
        assertTrue(customizer.isModuleCreated);
        assertArrayEquals(new JakartaValidationOption[] { expectedJakartaValidationOption }, customizer.configuredOptions);
        assertTrue(customizer.module.applyCalled);
    }

    private static Stream<Arguments> provideConfigOptions() {
        return Stream.of(
                Arguments.of((Consumer<Config>) c -> c.notNullableFieldIsRequired = true,
                        JakartaValidationOption.NOT_NULLABLE_FIELD_IS_REQUIRED),
                Arguments.of((Consumer<Config>) c -> c.notNullableMethodIsRequired = true,
                        JakartaValidationOption.NOT_NULLABLE_METHOD_IS_REQUIRED),
                Arguments.of((Consumer<Config>) c -> c.preferIdnEmailFormat = true,
                        JakartaValidationOption.PREFER_IDN_EMAIL_FORMAT),
                Arguments.of((Consumer<Config>) c -> c.includePatternExpressions = true,
                        JakartaValidationOption.INCLUDE_PATTERN_EXPRESSIONS));
    }

    private static class ConfigCustomizerSpy extends SchemaGeneratorConfigCustomizerJakartaValidation {

        private boolean isModuleCreated;
        private JakartaValidationOption[] configuredOptions;
        private ModuleSpy module;

        public ConfigCustomizerSpy(McpServerSchemaGeneratorJakartaValidationRuntimeConfig config) {
            super(config);
        }

        @Override
        Module createJakartaValidationModule(JakartaValidationOption[] options) {
            this.isModuleCreated = true;
            this.configuredOptions = options;
            this.module = new ModuleSpy(super.createJakartaValidationModule(options));
            return this.module;
        }
    }

    private static class ModuleSpy implements Module {
        private final Module delegate;
        boolean applyCalled;

        public ModuleSpy(Module delegate) {
            this.delegate = delegate;
        }

        @Override
        public void applyToConfigBuilder(SchemaGeneratorConfigBuilder builder) {
            applyCalled = true;
            delegate.applyToConfigBuilder(builder);
        }
    }

    private static class Config implements McpServerSchemaGeneratorJakartaValidationRuntimeConfig {

        boolean enabled;
        boolean notNullableFieldIsRequired;
        boolean notNullableMethodIsRequired;
        boolean preferIdnEmailFormat;
        boolean includePatternExpressions;

        static Config createDisableConfig() {
            var config = new Config();
            config.enabled = false;
            return config;
        }

        static Config createEnabledConfig(Consumer<Config> configModifier) {
            var config = new Config();
            config.enabled = true;
            configModifier.accept(config);
            return config;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public boolean notNullableFieldIsRequired() {
            return notNullableFieldIsRequired;
        }

        @Override
        public boolean notNullableMethodIsRequired() {
            return notNullableMethodIsRequired;
        }

        @Override
        public boolean preferIdnEmailFormat() {
            return preferIdnEmailFormat;
        }

        @Override
        public boolean includePatternExpressions() {
            return includePatternExpressions;
        }
    }
}