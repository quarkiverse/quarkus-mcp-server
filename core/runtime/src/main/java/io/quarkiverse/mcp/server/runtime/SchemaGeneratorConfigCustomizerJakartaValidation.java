package io.quarkiverse.mcp.server.runtime;

import java.util.ArrayList;

import jakarta.enterprise.context.Dependent;

import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationOption;

import io.quarkiverse.mcp.server.runtime.config.McpServerSchemaGeneratorJakartaValidationRuntimeConfig;

@Dependent
public class SchemaGeneratorConfigCustomizerJakartaValidation implements SchemaGeneratorConfigCustomizer {

    private final McpServerSchemaGeneratorJakartaValidationRuntimeConfig config;

    public SchemaGeneratorConfigCustomizerJakartaValidation(McpServerSchemaGeneratorJakartaValidationRuntimeConfig config) {
        this.config = config;
    }

    @Override
    public void customize(SchemaGeneratorConfigBuilder builder) {
        if (config.enabled()) {
            var configuredOptions = getConfiguredOptions();
            var jakartaValidationModule = createJakartaValidationModule(configuredOptions);
            builder.with(jakartaValidationModule);
        }
    }

    Module createJakartaValidationModule(JakartaValidationOption[] options) {
        return new JakartaValidationModule(options);
    }

    private JakartaValidationOption[] getConfiguredOptions() {
        var options = new ArrayList<JakartaValidationOption>();

        if (config.notNullableFieldIsRequired()) {
            options.add(JakartaValidationOption.NOT_NULLABLE_FIELD_IS_REQUIRED);
        }
        if (config.notNullableMethodIsRequired()) {
            options.add(JakartaValidationOption.NOT_NULLABLE_METHOD_IS_REQUIRED);
        }
        if (config.preferIdnEmailFormat()) {
            options.add(JakartaValidationOption.PREFER_IDN_EMAIL_FORMAT);
        }
        if (config.includePatternExpressions()) {
            options.add(JakartaValidationOption.INCLUDE_PATTERN_EXPRESSIONS);
        }

        return options.toArray(JakartaValidationOption[]::new);
    }
}
