package io.quarkiverse.mcp.server.runtime;

import java.util.ArrayList;

import jakarta.enterprise.context.Dependent;

import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;

import io.quarkiverse.mcp.server.runtime.config.McpServerSchemaGeneratorJacksonRuntimeConfig;

@Dependent
public class SchemaGeneratorConfigCustomizerJackson implements SchemaGeneratorConfigCustomizer {

    private final McpServerSchemaGeneratorJacksonRuntimeConfig config;

    public SchemaGeneratorConfigCustomizerJackson(McpServerSchemaGeneratorJacksonRuntimeConfig config) {
        this.config = config;
    }

    @Override
    public void customize(SchemaGeneratorConfigBuilder builder) {
        if (config.enabled()) {
            var configuredOptions = getConfiguredOptions();
            var jacksonModule = createJacksonModule(configuredOptions);
            builder.with(jacksonModule);
        }
    }

    Module createJacksonModule(JacksonOption[] options) {
        return new JacksonModule(options);
    }

    private JacksonOption[] getConfiguredOptions() {
        var options = new ArrayList<JacksonOption>();

        if (config.respectJsonPropertyOrder()) {
            options.add(JacksonOption.RESPECT_JSONPROPERTY_ORDER);
        }
        if (config.respectJsonPropertyRequired()) {
            options.add(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED);
        }
        if (config.flattenedEnumsFromJsonValue()) {
            options.add(JacksonOption.FLATTENED_ENUMS_FROM_JSONVALUE);
        }
        if (config.flattenedEnumsFromJsonProperty()) {
            options.add(JacksonOption.FLATTENED_ENUMS_FROM_JSONPROPERTY);
        }
        if (config.includeOnlyJsonPropertyAnnotatedMethods()) {
            options.add(JacksonOption.INCLUDE_ONLY_JSONPROPERTY_ANNOTATED_METHODS);
        }
        if (config.ignorePropertyNamingStrategy()) {
            options.add(JacksonOption.IGNORE_PROPERTY_NAMING_STRATEGY);
        }
        if (config.alwaysRefSubtypes()) {
            options.add(JacksonOption.ALWAYS_REF_SUBTYPES);
        }
        if (config.inlineTransformedSubtypes()) {
            options.add(JacksonOption.INLINE_TRANSFORMED_SUBTYPES);
        }
        if (config.skipSubtypeLookup()) {
            options.add(JacksonOption.SKIP_SUBTYPE_LOOKUP);
        }
        if (config.ignoreTypeInfoTransform()) {
            options.add(JacksonOption.IGNORE_TYPE_INFO_TRANSFORM);
        }
        if (config.jsonIdentityReferenceAlwaysAsId()) {
            options.add(JacksonOption.JSONIDENTITY_REFERENCE_ALWAYS_AS_ID);
        }

        return options.toArray(JacksonOption[]::new);
    }
}
