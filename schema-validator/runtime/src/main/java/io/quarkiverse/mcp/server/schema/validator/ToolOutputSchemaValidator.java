package io.quarkiverse.mcp.server.schema.validator;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.GlobalGuardrail;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.ToolOutputGuardrail;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.schema.validator.McpSchemaValidatorRuntimeConfig.Validation;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.json.schema.OutputUnit;
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
public class ToolOutputSchemaValidator extends ToolSchemaValidator<ToolResponse> implements ToolOutputGuardrail {

    private static final Logger LOG = Logger.getLogger(ToolOutputSchemaValidator.class);

    public ToolOutputSchemaValidator() {
        super("output");
    }

    @Override
    public Uni<Void> applyAsync(ToolOutputContext context) {
        Validation mode = config.tools().output();
        ToolResponse response = context.getResponse();
        if (mode == Validation.OFF || response.isError()) {
            return Uni.createFrom().voidItem();
        }
        // Tools without an output schema are not validated
        return validate(context.getTool(), context.getConnection().serverName(), response)
                .invoke(violation -> {
                    if (violation == null) {
                        return;
                    }
                    LOG.warnf("%s [connection: %s]", violation, context.getConnection().id());
                    if (mode == Validation.FAIL) {
                        context.setResponse(ToolResponse.error(violation));
                    }
                })
                .replaceWithVoid();
    }

    @Override
    protected Object schema(ToolInfo tool) {
        return tool.outputSchema();
    }

    @Override
    protected String violation(ToolInfo tool, Validator validator, ToolResponse response) {
        // Serialize the response the same way the server does, so the validated JSON is the JSON that is sent
        Object structuredContent = JsonObject.mapFrom(response).getValue("structuredContent");
        if (structuredContent == null) {
            return "Output validation error: Tool %s has an output schema but no structured content was provided"
                    .formatted(tool.name());
        }
        OutputUnit unit;
        try {
            unit = validator.validate(structuredContent);
        } catch (RuntimeException e) {
            // A problem with the schema itself, e.g. an unresolvable reference, is not a violation by the tool
            LOG.warnf(e, "Unable to validate the structured content of tool %s against its output schema", tool.name());
            return null;
        }
        if (unit.getValid()) {
            return null;
        }
        return "Output validation error: Invalid structured content for tool %s: %s".formatted(tool.name(),
                ToolSchemas.describe(unit, "the value does not match the output schema"));
    }

}
