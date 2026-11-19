package io.quarkiverse.mcp.server.schema.validator;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.GlobalGuardrail;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolInputGuardrail;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.runtime.FeatureKey;
import io.quarkiverse.mcp.server.schema.validator.McpSchemaValidatorRuntimeConfig.Validation;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.json.schema.OutputUnit;
import io.vertx.json.schema.Validator;

/**
 * A global input guardrail that validates the arguments of a tool call against the tool's {@code inputSchema}, as the MCP
 * specification requires servers to validate all tool inputs.
 * <p>
 * It has the highest priority, so it runs before all other input guardrails and checks the arguments the client sent.
 */
@GlobalGuardrail
@Priority(Integer.MAX_VALUE)
@Singleton
public class ToolInputSchemaValidator implements ToolInputGuardrail {

    private static final Logger LOG = Logger.getLogger(ToolInputSchemaValidator.class);

    @Inject
    Vertx vertx;

    @Inject
    McpSchemaValidatorRuntimeConfig config;

    // Keyed by tool name and server name, since tools with the same name can be bound to different servers; the tool
    // instance detects a tool that was removed and registered again
    private final ConcurrentMap<FeatureKey, CachedSchema> schemas = new ConcurrentHashMap<>();

    @Override
    public Uni<Void> applyAsync(ToolInputContext context) {
        Validation mode = config.tools().input();
        if (mode == Validation.OFF) {
            return Uni.createFrom().voidItem();
        }
        ToolInfo tool = context.getTool();
        CachedSchema schema = schema(tool, context.getConnection().serverName());
        if (schema.json() == null) {
            return Uni.createFrom().voidItem();
        }
        JsonObject arguments = context.getArguments() != null ? context.getArguments() : new JsonObject();
        // Schema compilation and validation are CPU bound; run them off the event loop like the request validation
        return Uni.createFrom().completionStage(vertx.<String> executeBlocking(() -> violation(tool, schema, arguments))
                .toCompletionStage())
                .invoke(violation -> {
                    if (violation == null) {
                        return;
                    }
                    LOG.warnf("%s [connection: %s]", violation, context.getConnection().id());
                    if (mode == Validation.FAIL) {
                        // A tool execution error (isError: true), so that the model can correct the arguments and retry
                        throw new ToolCallException(violation);
                    }
                })
                .replaceWithVoid();
    }

    private CachedSchema schema(ToolInfo tool, String serverName) {
        FeatureKey key = new FeatureKey(tool.name(), serverName);
        CachedSchema cached = schemas.get(key);
        if (cached == null || cached.tool() != tool) {
            cached = new CachedSchema(tool, ToolSchemas.toJson(tool.inputSchema()), new AtomicReference<>());
            schemas.put(key, cached);
        }
        return cached;
    }

    private String violation(ToolInfo tool, CachedSchema schema, JsonObject arguments) {
        Optional<Validator> validator = schema.validator().get();
        if (validator == null) {
            validator = ToolSchemas.newValidator(tool, schema.json(), "input");
            schema.validator().set(validator);
        }
        if (validator.isEmpty()) {
            return null;
        }
        OutputUnit unit;
        try {
            unit = validator.get().validate(arguments);
        } catch (RuntimeException e) {
            // A problem with the schema itself, e.g. an unresolvable reference, is not a client error
            LOG.warnf(e, "Unable to validate the arguments of tool %s against its input schema", tool.name());
            return null;
        }
        if (unit.getValid()) {
            return null;
        }
        return "Input validation error: Invalid arguments for tool %s: %s".formatted(tool.name(),
                ToolSchemas.describe(unit, "the arguments do not match the input schema"));
    }

    // The validator is compiled lazily off the event loop; null means not compiled yet, empty means not compilable
    private record CachedSchema(ToolInfo tool, JsonObject json, AtomicReference<Optional<Validator>> validator) {
    }

}
