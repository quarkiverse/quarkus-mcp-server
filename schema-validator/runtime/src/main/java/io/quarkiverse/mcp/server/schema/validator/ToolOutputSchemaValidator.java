package io.quarkiverse.mcp.server.schema.validator;

import static io.vertx.json.schema.OutputFormat.Basic;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.GlobalGuardrail;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.ToolOutputGuardrail;
import io.quarkiverse.mcp.server.ToolResponse;
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

    // Keyed by tool name; the tool instance detects a tool that was removed and registered again
    private final ConcurrentMap<String, CachedValidator> validators = new ConcurrentHashMap<>();

    @Override
    public Uni<Void> applyAsync(ToolOutputContext context) {
        ToolOutputValidation mode = config.toolOutput();
        ToolResponse response = context.getResponse();
        if (mode == ToolOutputValidation.OFF || response.isError()) {
            return Uni.createFrom().voidItem();
        }
        ToolInfo tool = context.getTool();
        // Schema compilation and validation are CPU bound; run them off the event loop like the request validation
        return Uni.createFrom().completionStage(vertx.<String> executeBlocking(() -> violation(tool, response))
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

    private String violation(ToolInfo tool, ToolResponse response) {
        Optional<Validator> validator = validator(tool);
        if (validator.isEmpty()) {
            return null;
        }
        // Serialize the response the same way the server does, so the validated JSON is the JSON that is sent
        Object structuredContent = JsonObject.mapFrom(response).getValue("structuredContent");
        if (structuredContent == null) {
            return "Output validation error: Tool %s has an output schema but no structured content was provided"
                    .formatted(tool.name());
        }
        OutputUnit unit = validator.get().validate(structuredContent);
        if (unit.getValid()) {
            return null;
        }
        return "Output validation error: Invalid structured content for tool %s: %s".formatted(tool.name(), describe(unit));
    }

    private Optional<Validator> validator(ToolInfo tool) {
        String key = tool.name();
        CachedValidator cached = validators.get(key);
        if (cached == null || cached.tool() != tool) {
            cached = new CachedValidator(tool, newValidator(tool, key));
            validators.put(key, cached);
        }
        return cached.validator();
    }

    private Optional<Validator> newValidator(ToolInfo tool, String key) {
        Object outputSchema = tool.asJson().getValue("outputSchema");
        if (outputSchema == null) {
            return Optional.empty();
        }
        JsonObject schemaJson = outputSchema instanceof JsonObject json ? json : JsonObject.mapFrom(outputSchema);
        // When a schema does not include a $schema field, it defaults to JSON Schema 2020-12
        String schemaDraft = schemaJson.getString("$schema");
        Draft draft = schemaDraft != null ? Draft.fromIdentifier(schemaDraft) : Draft.DRAFT202012;
        String uri = BASE_URI + key;
        // A repository per tool, so that local references such as "#/$defs/..." resolve within the tool's own schema
        SchemaRepository repository = SchemaRepository.create(new JsonSchemaOptions().setBaseUri(BASE_URI));
        repository.dereference(uri, JsonSchema.of(schemaJson));
        return Optional.of(repository.validator(repository.find(uri), new JsonSchemaOptions()
                .setOutputFormat(Basic)
                .setDraft(draft)));
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

    private record CachedValidator(ToolInfo tool, Optional<Validator> validator) {
    }

}
