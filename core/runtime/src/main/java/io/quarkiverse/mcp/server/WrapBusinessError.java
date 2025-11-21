package io.quarkiverse.mcp.server;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

/**
 * Wraps a matching exception thrown from a "feature" method with an exception that represents a business logic error and is
 * automatically converted to a failed response.
 * <p>
 * For example, if a {@link Tool} method throws an exception it's wrapped with a {@link ToolCallException} which is
 * automatically converted to a failed {@link ToolResponse}.
 *
 * @see Tool
 * @see ToolCallException
 */
@InterceptorBinding
@Retention(RUNTIME)
@Target({ METHOD, TYPE })
public @interface WrapBusinessError {

    /**
     * An exception is wrapped automatically if it's assignable to any of the specified classes.
     * <p>
     * By default, all exceptions are wrapped.
     */
    @Nonbinding
    public Class<? extends Throwable>[] value() default { Exception.class };

    /**
     * An exception is never wrapped automatically if it's assignable to any of the specified classes.
     * <p>
     * If both {@link #value()} and {@link #unless()} are specified, {@link #unless()} takes precedence.
     */
    @Nonbinding
    public Class<? extends Throwable>[] unless() default {};

}
