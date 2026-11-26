package io.quarkiverse.mcp.server.schema.validator;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.runtime.FeatureKey;
import io.quarkus.runtime.BlockingOperationControl;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.json.schema.Validator;

/**
 * Validates a value against a tool's input or output schema; shared by the tool guardrails.
 * <p>
 * The schema is compiled lazily and cached per tool.
 *
 * @param <T> the validated value
 */
abstract class ToolSchemaValidator<T> {

    @Inject
    Vertx vertx;

    @Inject
    McpSchemaValidatorRuntimeConfig config;

    // Keyed by tool name and server name, since tools with the same name can be bound to different servers; the tool
    // instance detects a tool that was removed and registered again
    private final ConcurrentMap<FeatureKey, CachedSchema> schemas = new ConcurrentHashMap<>();

    private final String kind;

    /**
     * @param kind "input" or "output", used in the schema URI and the log message
     */
    protected ToolSchemaValidator(String kind) {
        this.kind = kind;
    }

    /**
     * @return the schema of the tool, or {@code null} if the tool has none
     */
    protected abstract Object schema(ToolInfo tool);

    /**
     * @return the violation message, or {@code null} if the value is valid
     */
    protected abstract String violation(ToolInfo tool, Validator validator, T value);

    /**
     * Schema compilation and validation are CPU bound, so they are offloaded from the event loop. A thread that can block
     * validates inline instead, because an offloaded result completes on the event loop and the tool call would continue
     * there.
     *
     * @return the violation message, or {@code null} if the value is valid or the tool has no schema
     */
    protected Uni<String> validate(ToolInfo tool, String serverName, T value) {
        CachedSchema schema = cachedSchema(tool, serverName);
        if (schema.json() == null) {
            return Uni.createFrom().nullItem();
        }
        if (BlockingOperationControl.isBlockingAllowed()) {
            return Uni.createFrom().item(violation(tool, schema, value));
        }
        return Uni.createFrom().completionStage(vertx.<String> executeBlocking(() -> violation(tool, schema, value))
                .toCompletionStage());
    }

    private String violation(ToolInfo tool, CachedSchema schema, T value) {
        Optional<Validator> validator = schema.validator().get();
        if (validator == null) {
            validator = ToolSchemas.newValidator(tool, schema.json(), kind);
            schema.validator().set(validator);
        }
        return validator.isEmpty() ? null : violation(tool, validator.get(), value);
    }

    private CachedSchema cachedSchema(ToolInfo tool, String serverName) {
        FeatureKey key = new FeatureKey(tool.name(), serverName);
        CachedSchema cached = schemas.get(key);
        if (cached == null || cached.tool() != tool) {
            cached = new CachedSchema(tool, ToolSchemas.toJson(schema(tool)), new AtomicReference<>());
            schemas.put(key, cached);
        }
        return cached;
    }

    // The validator is compiled lazily; null means not compiled yet, empty means not compilable
    private record CachedSchema(ToolInfo tool, JsonObject json, AtomicReference<Optional<Validator>> validator) {
    }

}
