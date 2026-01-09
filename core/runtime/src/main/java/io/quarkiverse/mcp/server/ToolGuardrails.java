package io.quarkiverse.mcp.server;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.smallrye.common.annotation.Experimental;

/**
 * Associate guardrails with a tool call.
 *
 * @see Tool
 * @see ToolInputGuardrail
 * @see ToolOutputGuardrail
 */
@Experimental("This API is experimental and may change in the future")
@Retention(RUNTIME)
@Target(METHOD)
public @interface ToolGuardrails {

    /**
     * Input guardrails can be used to validate and/or transform the arguments of a tool call.
     * <p>
     * The guardrails are executed in the specified order before a tool call.
     *
     * @see ToolInputGuardrail
     */
    Class<? extends ToolInputGuardrail>[] input() default {};

    /**
     * Output guardrails can be used to validate and/or transform the result of a tool call.
     * <p>
     * The guardrails are executed in the specified order after a tool call.
     *
     * @see ToolOutputGuardrail
     */
    Class<? extends ToolOutputGuardrail>[] output() default {};
}
