package io.quarkiverse.mcp.server.hibernate.validator.runtime;

import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.hibernate.validator.ConstraintViolationConverter;
import io.quarkiverse.mcp.server.runtime.Feature;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.quarkus.arc.DefaultBean;

@Singleton
@DefaultBean
class ConstraintViolationConverterImpl implements ConstraintViolationConverter {

    @Inject
    McpServersRuntimeConfig config;

    @Override
    public Exception convert(ConstraintViolationException e, FeatureContext context) {
        String message = e.getConstraintViolations()
                .stream()
                .map(ConstraintViolationConverterImpl::constraintViolationToString)
                .collect(Collectors.joining(", "));
        if (context.feature() == Feature.TOOL) {
            // Special handling for tools
            McpServerRuntimeConfig serverConfig = config.servers().get(context.serverName());
            return switch (serverConfig.tools().inputValidationError()) {
                case PROTOCOL -> new McpException(message, JsonRpcErrorCodes.INVALID_PARAMS);
                case TOOL -> new ToolCallException(message);
                default -> throw new IllegalArgumentException(
                        "Unexpected value: " + serverConfig.tools().inputValidationError());
            };
        } else {
            return new McpException(message, JsonRpcErrorCodes.INVALID_PARAMS);
        }
    }

    private static String constraintViolationToString(ConstraintViolation<?> cv) {
        return cv.getPropertyPath() + ": " + cv.getMessage();
    }

}
