package io.quarkiverse.mcp.server.hibernate.validator;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import io.quarkiverse.mcp.server.Feature;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.ToolCallException;

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
     * @deprecated Use {@link #convert(ConstraintViolationException, FeatureContext)} instead
     */
    @Deprecated(since = "1.9.0", forRemoval = true)
    default Exception convert(ConstraintViolationException exception) {
        return exception;
    }

    /**
     * @param exception (must not be {@code null})
     * @param context (must not be {@code null})
     * @return the resulting exception
     */
    default Exception convert(ConstraintViolationException exception, FeatureContext context) {
        return convert(exception);
    }

    record FeatureContext(Feature feat, String serverName) {

        public FeatureContext {
            if (feat == null) {
                throw new IllegalArgumentException("feature must not be null");
            }
            if (serverName == null) {
                throw new IllegalArgumentException("serverName must not be null");
            }
        }

        /**
         * @return the feature
         * @deprecated use {@link #feat()} instead; this accessor returns the deprecated
         *             {@link io.quarkiverse.mcp.server.runtime.Feature} and is kept only for backward compatibility
         */
        @Deprecated(since = "2.1.0", forRemoval = true)
        public io.quarkiverse.mcp.server.runtime.Feature feature() {
            return io.quarkiverse.mcp.server.runtime.Feature.valueOf(feat.name());
        }

    }

}
