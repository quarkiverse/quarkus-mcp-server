package io.quarkiverse.mcp.server.hibernate.validator.runtime;

import java.util.stream.Collectors;

import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.hibernate.validator.ConstraintViolationConverter;
import io.quarkus.arc.DefaultBean;

@Singleton
@DefaultBean
class ConstraintViolationConverterImpl implements ConstraintViolationConverter {

    @Override
    public Exception convert(ConstraintViolationException e) {
        String message = e.getConstraintViolations()
                .stream()
                .map(ConstraintViolationConverterImpl::constraintViolationToString)
                .collect(Collectors.joining(", "));
        return new McpException(message, JsonRpcErrorCodes.INVALID_PARAMS);
    }

    private static String constraintViolationToString(ConstraintViolation<?> cv) {
        return cv.getPropertyPath() + ": " + cv.getMessage();
    }

}
