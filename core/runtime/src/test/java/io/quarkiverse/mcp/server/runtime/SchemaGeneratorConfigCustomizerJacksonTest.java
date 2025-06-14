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
import com.github.victools.jsonschema.module.jackson.JacksonOption;

import io.quarkiverse.mcp.server.runtime.config.McpServerSchemaGeneratorJacksonRuntimeConfig;

class SchemaGeneratorConfigCustomizerJacksonTest {

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
    void customize_enabled_with_options(Consumer<Config> configModifier, JacksonOption expectedJacksonOption) {
        // Given
        var config = Config.createEnabledConfig(configModifier);
        var customizer = new ConfigCustomizerSpy(config);

        // When
        customizer.customize(new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .without(Option.SCHEMA_VERSION_INDICATOR));

        // Then
        assertTrue(customizer.isModuleCreated);
        assertArrayEquals(new JacksonOption[] { expectedJacksonOption }, customizer.configuredOptions);
        assertTrue(customizer.module.applyCalled);
    }

    private static Stream<Arguments> provideConfigOptions() {
        return Stream.of(
                Arguments.of((Consumer<Config>) c -> c.respectJsonPropertyOrder = true,
                        JacksonOption.RESPECT_JSONPROPERTY_ORDER),
                Arguments.of((Consumer<Config>) c -> c.respectJsonPropertyRequired = true,
                        JacksonOption.RESPECT_JSONPROPERTY_REQUIRED),
                Arguments.of((Consumer<Config>) c -> c.flattenedEnumsFromJsonValue = true,
                        JacksonOption.FLATTENED_ENUMS_FROM_JSONVALUE),
                Arguments.of((Consumer<Config>) c -> c.flattenedEnumsFromJsonProperty = true,
                        JacksonOption.FLATTENED_ENUMS_FROM_JSONPROPERTY),
                Arguments.of((Consumer<Config>) c -> c.includeOnlyJsonPropertyAnnotatedMethods = true,
                        JacksonOption.INCLUDE_ONLY_JSONPROPERTY_ANNOTATED_METHODS),
                Arguments.of((Consumer<Config>) c -> c.ignorePropertyNamingStrategy = true,
                        JacksonOption.IGNORE_PROPERTY_NAMING_STRATEGY),
                Arguments.of((Consumer<Config>) c -> c.alwaysRefSubtypes = true,
                        JacksonOption.ALWAYS_REF_SUBTYPES),
                Arguments.of((Consumer<Config>) c -> c.inlineTransformedSubtypes = true,
                        JacksonOption.INLINE_TRANSFORMED_SUBTYPES),
                Arguments.of((Consumer<Config>) c -> c.skipSubtypeLookup = true,
                        JacksonOption.SKIP_SUBTYPE_LOOKUP),
                Arguments.of((Consumer<Config>) c -> c.ignoreTypeInfoTransform = true,
                        JacksonOption.IGNORE_TYPE_INFO_TRANSFORM),
                Arguments.of((Consumer<Config>) c -> c.jsonIdentityReferenceAlwaysAsId = true,
                        JacksonOption.JSONIDENTITY_REFERENCE_ALWAYS_AS_ID));
    }

    private static class ConfigCustomizerSpy extends SchemaGeneratorConfigCustomizerJackson {

        private boolean isModuleCreated;
        private JacksonOption[] configuredOptions;
        private ModuleSpy module;

        public ConfigCustomizerSpy(McpServerSchemaGeneratorJacksonRuntimeConfig config) {
            super(config);
        }

        @Override
        Module createJacksonModule(JacksonOption[] options) {
            this.isModuleCreated = true;
            this.configuredOptions = options;
            this.module = new ModuleSpy(super.createJacksonModule(options));
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

    private static class Config implements McpServerSchemaGeneratorJacksonRuntimeConfig {

        boolean enabled;
        boolean respectJsonPropertyOrder;
        boolean respectJsonPropertyRequired;
        boolean flattenedEnumsFromJsonValue;
        boolean flattenedEnumsFromJsonProperty;
        boolean includeOnlyJsonPropertyAnnotatedMethods;
        boolean ignorePropertyNamingStrategy;
        boolean alwaysRefSubtypes;
        boolean inlineTransformedSubtypes;
        boolean skipSubtypeLookup;
        boolean ignoreTypeInfoTransform;
        boolean jsonIdentityReferenceAlwaysAsId;

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
        public boolean respectJsonPropertyOrder() {
            return respectJsonPropertyOrder;
        }

        @Override
        public boolean respectJsonPropertyRequired() {
            return respectJsonPropertyRequired;
        }

        @Override
        public boolean flattenedEnumsFromJsonValue() {
            return flattenedEnumsFromJsonValue;
        }

        @Override
        public boolean flattenedEnumsFromJsonProperty() {
            return flattenedEnumsFromJsonProperty;
        }

        @Override
        public boolean includeOnlyJsonPropertyAnnotatedMethods() {
            return includeOnlyJsonPropertyAnnotatedMethods;
        }

        @Override
        public boolean ignorePropertyNamingStrategy() {
            return ignorePropertyNamingStrategy;
        }

        @Override
        public boolean alwaysRefSubtypes() {
            return alwaysRefSubtypes;
        }

        @Override
        public boolean inlineTransformedSubtypes() {
            return inlineTransformedSubtypes;
        }

        @Override
        public boolean skipSubtypeLookup() {
            return skipSubtypeLookup;
        }

        @Override
        public boolean ignoreTypeInfoTransform() {
            return ignoreTypeInfoTransform;
        }

        @Override
        public boolean jsonIdentityReferenceAlwaysAsId() {
            return jsonIdentityReferenceAlwaysAsId;
        }
    }
}
