package io.quarkiverse.mcp.server.runtime;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;

import io.quarkiverse.mcp.server.DefaultValueConverter;
import io.quarkiverse.mcp.server.GlobalInputSchemaGenerator;
import io.quarkiverse.mcp.server.GlobalOutputSchemaGenerator;
import io.quarkiverse.mcp.server.ToolManager.ToolArgument;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkus.arc.All;
import io.quarkus.arc.DefaultBean;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DefaultBean
@Singleton
public class DefaultSchemaGenerator implements GlobalInputSchemaGenerator, GlobalOutputSchemaGenerator {

    private final SchemaGenerator schemaGenerator;

    private final Map<String, Class<?>> toolArgumentHolders;

    final Map<Type, DefaultValueConverter<?>> defaultValueConverters;

    public DefaultSchemaGenerator(@All List<SchemaGeneratorConfigCustomizer> schemaGeneratorConfigCustomizers,
            McpMetadata metadata) {
        this.schemaGenerator = constructSchemaGenerator(schemaGeneratorConfigCustomizers);
        this.toolArgumentHolders = metadata.toolArgumentHolders();
        this.defaultValueConverters = metadata.defaultValueConverters();
    }

    @Override
    public InputSchema generate(ToolInfo tool) {
        Class<?> holder = toolArgumentHolders.get(tool.name());
        Object properties;
        if (holder != null) {
            properties = generateSchema(holder, tool.arguments());
        } else {
            JsonObject props = new JsonObject();
            for (ToolArgument a : tool.arguments()) {
                props.put(a.name(), generateSchema(a.type(), a.description(), a.defaultValue()));
            }
            properties = props;
        }
        JsonArray required = new JsonArray();
        for (ToolArgument a : tool.arguments()) {
            if (a.required()) {
                required.add(a.name());
            }
        }
        return new InputSchemaImpl(new JsonObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", required));
    }

    record InputSchemaImpl(JsonObject value) implements InputSchema {

        @Override
        public String asJson() {
            return value.toString();
        }

    }

    @Override
    public Object generate(Class<?> from) {
        return schemaGenerator.generateSchema(from);
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

    Object generateSchema(Class<?> holderClass, List<ToolArgument> toolArguments) {
        JsonNode jsonNode = schemaGenerator.generateSchema(holderClass);
        if (jsonNode.isObject()) {
            ObjectNode objectNode = (ObjectNode) jsonNode;
            for (ToolArgument arg : toolArguments) {
                JsonNode node = objectNode.get(arg.name());
                if (node != null) {
                    postProcessJsonNode(node, arg.type(), arg.description(), arg.defaultValue());
                }
            }
            return objectNode.get("properties");
        }
        return jsonNode;
    }

    Object generateSchema(Type type, String description, String defaultValue) {
        JsonNode jsonNode = schemaGenerator.generateSchema(type);
        postProcessJsonNode(jsonNode, type, description, defaultValue);
        return jsonNode;
    }

    private void postProcessJsonNode(JsonNode jsonNode, Type type, String description, String defaultValue) {
        if (jsonNode.isObject()) {
            ObjectNode objectNode = (ObjectNode) jsonNode;
            if (Types.isOptional(type)) {
                // The generated schema for Optional<List<String>> looks like:
                // {"type":"object","properties":{"value":{"type":"array","items":{"type":"string"}}}}
                // We need to extract the value property and replace the original object node
                ObjectNode valueProp = objectNode.withObjectProperty("properties").withObjectProperty("value");
                if (valueProp != null) {
                    objectNode = valueProp;
                }
            }
            if (description != null && !description.isBlank()) {
                objectNode.put("description", description);
            }
            if (defaultValue != null) {
                Object converted = FeatureManagerBase.convert(defaultValue, type, defaultValueConverters);
                objectNode.putPOJO("default", converted);
            }
        }
    }

}
