package io.quarkiverse.mcp.server.schema.validator;

import static io.vertx.json.schema.OutputFormat.Basic;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.vertx.core.json.JsonObject;
import io.vertx.json.schema.Draft;
import io.vertx.json.schema.JsonSchema;
import io.vertx.json.schema.JsonSchemaOptions;
import io.vertx.json.schema.OutputUnit;
import io.vertx.json.schema.SchemaRepository;
import io.vertx.json.schema.Validator;

/**
 * Compiles a tool's input or output schema and describes validation errors; shared by the tool guardrails.
 */
final class ToolSchemas {

    private static final Logger LOG = Logger.getLogger(ToolSchemas.class);

    private static final String BASE_URI = "https://quarkiverse.io/quarkus-mcp-server/tools/";

    private ToolSchemas() {
    }

    static JsonObject toJson(Object schema) {
        if (schema == null) {
            return null;
        }
        JsonObject json = schema instanceof JsonObject jsonObject ? jsonObject : JsonObject.mapFrom(schema);
        // A generated schema can hold nested Maps and Lists, which the validator does not read as subschemas; a copy
        // made from the encoded form holds only JsonObject and JsonArray values
        return new JsonObject(json.encode());
    }

    /**
     * @param kind "input" or "output", used in the schema URI and the log message
     * @return the validator, or an empty optional if the schema cannot be compiled
     */
    static Optional<Validator> newValidator(ToolInfo tool, JsonObject schemaJson, String kind) {
        try {
            // When a schema does not include a $schema field, it defaults to JSON Schema 2020-12
            String schemaDraft = schemaJson.getString("$schema");
            Draft draft = schemaDraft != null ? Draft.fromIdentifier(schemaDraft) : Draft.DRAFT202012;
            String uri = BASE_URI + tool.name();
            // A repository per schema, so that local references such as "#/$defs/..." resolve within the schema itself
            SchemaRepository repository = SchemaRepository.create(new JsonSchemaOptions().setBaseUri(BASE_URI));
            repository.dereference(uri, JsonSchema.of(schemaJson));
            return Optional.of(repository.validator(repository.find(uri), new JsonSchemaOptions()
                    .setOutputFormat(Basic)
                    .setDraft(draft)));
        } catch (RuntimeException e) {
            // A schema that cannot be compiled is skipped, so that it does not break the tool
            LOG.warnf(e, "Unable to compile the %s schema of tool %s; it is not validated", kind, tool.name());
            return Optional.empty();
        }
    }

    static String describe(OutputUnit unit, String fallback) {
        List<OutputUnit> errors = unit.getErrors();
        if (errors != null && !errors.isEmpty()) {
            return errors.stream()
                    .filter(Objects::nonNull)
                    .map(e -> location(e) + e.getError())
                    .collect(Collectors.joining(", ", "[", "]"));
        }
        return unit.getError() != null ? location(unit) + unit.getError() : fallback;
    }

    private static String location(OutputUnit unit) {
        String location = unit.getInstanceLocation();
        return location == null || location.isEmpty() ? "" : location + ": ";
    }

}
