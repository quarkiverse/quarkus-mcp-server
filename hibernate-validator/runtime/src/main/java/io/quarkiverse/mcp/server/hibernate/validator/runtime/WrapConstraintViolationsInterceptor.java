package io.quarkiverse.mcp.server.hibernate.validator.runtime;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.validation.ConstraintViolationException;

import io.quarkiverse.mcp.server.hibernate.validator.ConstraintViolationConverter;

/**
 * Wraps a business method and transforms {@link ConstraintViolationException} to another exception.
 *
 * @see ConstraintViolationConverter
 */
@WrapConstraintViolations
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_AFTER + 100) // MethodValidationInterceptor has priority 4800
public class WrapConstraintViolationsInterceptor {

    @Inject
    ConstraintViolationConverter converter;

    @AroundInvoke
    Object intercept(InvocationContext context) throws Exception {
        try {
            return context.proceed();
        } catch (ConstraintViolationException e) {
            throw converter.convert(e);
        }
    }

}
