package io.quarkiverse.mcp.server.hibernate.validator;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.runtime.Feature;

/**
 * Converts a {@link ConstraintViolationException} into another exception. The converted exception is re-thrown.
 * <p>
 * The container provides a default implementation of this interface.
 * <p>
 * For tools, a {@link ConstraintViolationException} is converted into a {@link ToolCallException} by default. The message
 * contains {@link ConstraintViolation#getPropertyPath()} and {@link ConstraintViolation#getMessage()} for each violation.
 * <p>
 * For any other feature, a {@link ConstraintViolationException} is converted into {@link McpException} by default. The JSON-RPC
 * error code is {@code -32602} (invalid params) and the message contains {@link ConstraintViolation#getPropertyPath()} and
 * {@link ConstraintViolation#getMessage()} for each violation.
 */
public interface ConstraintViolationConverter {

    /**
     * @param exception (must not be {@code null})
     * @return the resulting exception
     * @deprecated Use {@link #convert(ConstraintViolationException, Feature)} instead
     */
    @Deprecated(since = "1.9.0", forRemoval = true)
    default Exception convert(ConstraintViolationException exception) {
        return exception;
    }

    /**
     * @param exception (must not be {@code null})
     * @param feature (must not be {@code null})
     * @return the resulting exception
     */
    default Exception convert(ConstraintViolationException exception, FeatureContext context) {
        return convert(exception);
    }

    record FeatureContext(Feature feature, String serverName) {

        public FeatureContext {
            if (feature == null) {
                throw new IllegalArgumentException("feature must not be null");
            }
            if (serverName == null) {
                throw new IllegalArgumentException("serverName must not be null");
            }
        }

    }

}
