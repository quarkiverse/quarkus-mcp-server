package io.quarkiverse.mcp.server.runtime;

import java.lang.reflect.Type;
import java.util.List;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;

import io.quarkiverse.mcp.server.OutputSchemaGenerator;
import io.quarkus.arc.All;

@Singleton
public class DefaultSchemaGenerator implements OutputSchemaGenerator {

    private final SchemaGenerator schemaGenerator;

    private DefaultSchemaGenerator(@All List<SchemaGeneratorConfigCustomizer> schemaGeneratorConfigCustomizers) {
        this.schemaGenerator = constructSchemaGenerator(schemaGeneratorConfigCustomizers);
    }

    JsonNode generateSchema(Type type) {
        return schemaGenerator.generateSchema(type);
    }

    @Override
    public Object generate(Class<?> from) {
        return generateSchema(from);
    }

    private static SchemaGenerator constructSchemaGenerator(
            List<SchemaGeneratorConfigCustomizer> schemaGeneratorConfigCustomizers) {
        var configBuilder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .without(Option.SCHEMA_VERSION_INDICATOR);
        for (SchemaGeneratorConfigCustomizer customizer : schemaGeneratorConfigCustomizers) {
            customizer.customize(configBuilder);
        }
        return new SchemaGenerator(configBuilder.build());
    }

}
