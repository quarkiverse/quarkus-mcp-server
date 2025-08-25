package io.quarkiverse.mcp.server.runtime;

import java.lang.reflect.Type;
import java.util.List;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;

import io.quarkiverse.mcp.server.AudioContent;
import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.EmbeddedResource;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.OutputSchemaGenerator;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.ResourceLink;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.arc.All;
import io.quarkus.jackson.ObjectMapperCustomizer;

@Singleton
public class DefaultSchemaGenerator implements OutputSchemaGenerator, ObjectMapperCustomizer {

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

    @Override
    public void customize(ObjectMapper objectMapper) {
        objectMapper.addMixIn(ToolResponse.class, ResponseMixin.class);
        objectMapper.addMixIn(PromptResponse.class, ResponseMixin.class);
        objectMapper.addMixIn(ResourceResponse.class, ResponseMixin.class);
        objectMapper.addMixIn(CompletionResponse.class, ResponseMixin.class);

        objectMapper.addMixIn(TextResourceContents.class, ResponseMixin.class);
        objectMapper.addMixIn(BlobResourceContents.class, ResponseMixin.class);

        objectMapper.addMixIn(AudioContent.class, ResponseMixin.class);
        objectMapper.addMixIn(EmbeddedResource.class, ResponseMixin.class);
        objectMapper.addMixIn(ImageContent.class, ResponseMixin.class);
        objectMapper.addMixIn(ResourceLink.class, ResponseMixin.class);
        objectMapper.addMixIn(TextContent.class, ResponseMixin.class);

    }

    @JsonInclude(Include.NON_NULL)
    static abstract class ResponseMixin {

    }

}
