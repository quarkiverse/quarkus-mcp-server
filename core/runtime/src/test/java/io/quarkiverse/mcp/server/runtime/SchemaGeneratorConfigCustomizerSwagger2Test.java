package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;

import io.quarkiverse.mcp.server.runtime.config.McpServerSchemaGeneratorSwagger2RuntimeConfig;

class SchemaGeneratorConfigCustomizerSwagger2Test {

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

    @Test
    void customize_enabled() {
        // Given
        var config = Config.createEnabledConfig();
        var customizer = new ConfigCustomizerSpy(config);

        // When
        customizer.customize(new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .without(Option.SCHEMA_VERSION_INDICATOR));

        // Then
        assertTrue(customizer.isModuleCreated);
        assertTrue(customizer.module.applyCalled);
    }

    private static class ConfigCustomizerSpy extends SchemaGeneratorConfigCustomizerSwagger2 {

        private boolean isModuleCreated;
        private ModuleSpy module;

        public ConfigCustomizerSpy(McpServerSchemaGeneratorSwagger2RuntimeConfig config) {
            super(config);
        }

        @Override
        Module createSwagger2Module() {
            this.isModuleCreated = true;
            this.module = new ModuleSpy(super.createSwagger2Module());
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

    private static class Config implements McpServerSchemaGeneratorSwagger2RuntimeConfig {

        boolean enabled;

        static Config createDisableConfig() {
            var config = new Config();
            config.enabled = false;
            return config;
        }

        static Config createEnabledConfig() {
            var config = new Config();
            config.enabled = true;
            return config;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }
    }
}
