package io.quarkiverse.mcp.server.runtime;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;

import io.quarkus.arc.All;

@ApplicationScoped
public class SchemaGeneratorProvider {

    private final SchemaGenerator schemaGenerator;

    public SchemaGeneratorProvider(@All List<SchemaGeneratorConfigCustomizer> schemaGeneratorConfigCustomizers) {
        var configBuilder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .without(Option.SCHEMA_VERSION_INDICATOR);

        // Apply customizers
        for (SchemaGeneratorConfigCustomizer customizer : schemaGeneratorConfigCustomizers) {
            customizer.customize(configBuilder);
        }

        this.schemaGenerator = new SchemaGenerator(configBuilder.build());
    }

    @Produces
    @ApplicationScoped
    public SchemaGenerator getSchemaGenerator() {
        return schemaGenerator;
    }
}
