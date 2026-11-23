package io.quarkiverse.mcp.server.schema.validator;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.GlobalGuardrail;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolInputGuardrail;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.schema.validator.McpSchemaValidatorRuntimeConfig.Validation;
import io.smallrye.mutiny.Uni;
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
public class ToolInputSchemaValidator extends ToolSchemaValidator<JsonObject> implements ToolInputGuardrail {

    private static final Logger LOG = Logger.getLogger(ToolInputSchemaValidator.class);

    public ToolInputSchemaValidator() {
        super("input");
    }

    @Override
    public Uni<Void> applyAsync(ToolInputContext context) {
        Validation mode = config.tools().input();
        if (mode == Validation.OFF) {
            return Uni.createFrom().voidItem();
        }
        JsonObject arguments = context.getArguments() != null ? context.getArguments() : new JsonObject();
        return validate(context.getTool(), context.getConnection().serverName(), arguments)
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

    @Override
    protected Object schema(ToolInfo tool) {
        return tool.inputSchema();
    }

    @Override
    protected String violation(ToolInfo tool, Validator validator, JsonObject arguments) {
        OutputUnit unit;
        try {
            unit = validator.validate(arguments);
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

}
