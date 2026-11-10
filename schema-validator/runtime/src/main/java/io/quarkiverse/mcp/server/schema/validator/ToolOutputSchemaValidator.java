package io.quarkiverse.mcp.server.schema.validator;

import static io.vertx.json.schema.OutputFormat.Basic;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.GlobalGuardrail;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.ToolOutputGuardrail;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.runtime.FeatureKey;
import io.quarkiverse.mcp.server.schema.validator.McpSchemaValidatorRuntimeConfig.ToolOutputValidation;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.json.schema.Draft;
import io.vertx.json.schema.JsonSchema;
import io.vertx.json.schema.JsonSchemaOptions;
import io.vertx.json.schema.OutputUnit;
import io.vertx.json.schema.SchemaRepository;
import io.vertx.json.schema.Validator;

/**
 * A global output guardrail that validates the {@code structuredContent} of a successful tool result against the tool's
 * {@code outputSchema}, as the MCP specification requires servers to return conforming structured results.
 * <p>
 * It has the lowest priority, so it runs after all other output guardrails and checks the response that is actually sent.
 */
@GlobalGuardrail
@Priority(Integer.MIN_VALUE)
@Singleton
public class ToolOutputSchemaValidator implements ToolOutputGuardrail {

    private static final Logger LOG = Logger.getLogger(ToolOutputSchemaValidator.class);

    private static final String BASE_URI = "https://quarkiverse.io/quarkus-mcp-server/tools/";

    @Inject
    Vertx vertx;

    @Inject
    McpSchemaValidatorRuntimeConfig config;

    // Keyed by tool name and server name, since tools with the same name can be bound to different servers; the tool
    // instance detects a tool that was removed and registered again
    private final ConcurrentMap<FeatureKey, CachedSchema> schemas = new ConcurrentHashMap<>();

    @Override
    public Uni<Void> applyAsync(ToolOutputContext context) {
        ToolOutputValidation mode = config.toolOutput();
        ToolResponse response = context.getResponse();
        if (mode == ToolOutputValidation.OFF || response.isError()) {
            return Uni.createFrom().voidItem();
        }
        ToolInfo tool = context.getTool();
        CachedSchema schema = schema(tool, context.getConnection().serverName());
        if (schema.json() == null) {
            // Tools without an output schema are not validated, so there is nothing to offload
            return Uni.createFrom().voidItem();
        }
        // Schema compilation and validation are CPU bound; run them off the event loop like the request validation
        return Uni.createFrom().completionStage(vertx.<String> executeBlocking(() -> violation(tool, schema, response))
                .toCompletionStage())
                .invoke(violation -> {
                    if (violation == null) {
                        return;
                    }
                    LOG.warnf("%s [connection: %s]", violation, context.getConnection().id());
                    if (mode == ToolOutputValidation.FAIL) {
                        context.setResponse(ToolResponse.error(violation));
                    }
                })
                .replaceWithVoid();
    }

    private CachedSchema schema(ToolInfo tool, String serverName) {
        FeatureKey key = new FeatureKey(tool.name(), serverName);
        CachedSchema cached = schemas.get(key);
        if (cached == null || cached.tool() != tool) {
            Object outputSchema = tool.outputSchema();
            JsonObject json = outputSchema == null || outputSchema instanceof JsonObject ? (JsonObject) outputSchema
                    : JsonObject.mapFrom(outputSchema);
            cached = new CachedSchema(tool, json, new AtomicReference<>());
            schemas.put(key, cached);
        }
        return cached;
    }

    private String violation(ToolInfo tool, CachedSchema schema, ToolResponse response) {
        Optional<Validator> validator = schema.validator().get();
        if (validator == null) {
            validator = newValidator(tool, schema.json());
            schema.validator().set(validator);
        }
        if (validator.isEmpty()) {
            return null;
        }
        // Serialize the response the same way the server does, so the validated JSON is the JSON that is sent
        Object structuredContent = JsonObject.mapFrom(response).getValue("structuredContent");
        if (structuredContent == null) {
            return "Output validation error: Tool %s has an output schema but no structured content was provided"
                    .formatted(tool.name());
        }
        OutputUnit unit;
        try {
            unit = validator.get().validate(structuredContent);
        } catch (RuntimeException e) {
            // A problem with the schema itself, e.g. an unresolvable reference, is not a violation by the tool
            LOG.warnf(e, "Unable to validate the structured content of tool %s against its output schema", tool.name());
            return null;
        }
        if (unit.getValid()) {
            return null;
        }
        return "Output validation error: Invalid structured content for tool %s: %s".formatted(tool.name(), describe(unit));
    }

    private Optional<Validator> newValidator(ToolInfo tool, JsonObject schemaJson) {
        try {
            // When a schema does not include a $schema field, it defaults to JSON Schema 2020-12
            String schemaDraft = schemaJson.getString("$schema");
            Draft draft = schemaDraft != null ? Draft.fromIdentifier(schemaDraft) : Draft.DRAFT202012;
            String uri = BASE_URI + tool.name();
            // A repository per tool, so that local references such as "#/$defs/..." resolve within the tool's own schema
            SchemaRepository repository = SchemaRepository.create(new JsonSchemaOptions().setBaseUri(BASE_URI));
            repository.dereference(uri, JsonSchema.of(schemaJson));
            return Optional.of(repository.validator(repository.find(uri), new JsonSchemaOptions()
                    .setOutputFormat(Basic)
                    .setDraft(draft)));
        } catch (RuntimeException e) {
            // An output schema that cannot be compiled is skipped, so that it does not break the tool
            LOG.warnf(e, "Unable to compile the output schema of tool %s; its structured content is not validated",
                    tool.name());
            return Optional.empty();
        }
    }

    private static String describe(OutputUnit unit) {
        List<OutputUnit> errors = unit.getErrors();
        if (errors != null && !errors.isEmpty()) {
            return errors.stream()
                    .filter(Objects::nonNull)
                    .map(e -> location(e) + e.getError())
                    .collect(Collectors.joining(", ", "[", "]"));
        }
        return unit.getError() != null ? location(unit) + unit.getError() : "the value does not match the output schema";
    }

    private static String location(OutputUnit unit) {
        String location = unit.getInstanceLocation();
        return location == null || location.isEmpty() ? "" : location + ": ";
    }

    // The validator is compiled lazily off the event loop; null means not compiled yet, empty means not compilable
    private record CachedSchema(ToolInfo tool, JsonObject json, AtomicReference<Optional<Validator>> validator) {
    }

}
