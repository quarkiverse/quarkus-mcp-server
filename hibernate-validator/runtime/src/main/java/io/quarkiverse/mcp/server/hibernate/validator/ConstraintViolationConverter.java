package io.quarkiverse.mcp.server.hibernate.validator;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import io.quarkiverse.mcp.server.McpException;

/**
 * Converts a {@link ConstraintViolationException} into another exception. The converted exception is re-thrown.
 * <p>
 * By default, a {@link ConstraintViolationException} is converted into {@link McpException} with JSON-RPC error code
 * {@code -32602} (invalid params) and the message contains {@link ConstraintViolation#getPropertyPath()} and
 * {@link ConstraintViolation#getMessage()} for each violation.
 */
public interface ConstraintViolationConverter {

    /**
     *
     * @param exception (must not be {@code null})
     * @return the resulting exception
     */
    Exception convert(ConstraintViolationException exception);

}
