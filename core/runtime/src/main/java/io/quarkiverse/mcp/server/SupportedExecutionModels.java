package io.quarkiverse.mcp.server;

import static io.quarkiverse.mcp.server.ExecutionModel.EVENT_LOOP;
import static io.quarkiverse.mcp.server.ExecutionModel.VIRTUAL_THREAD;
import static io.quarkiverse.mcp.server.ExecutionModel.WORKER_THREAD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Defines the supported execution models for a tool guardrail.
 *
 * @see ToolInputGuardrail
 * @see ToolOutputGuardrail
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface SupportedExecutionModels {

    /**
     * The supported execution models.
     */
    ExecutionModel[] value() default { EVENT_LOOP, VIRTUAL_THREAD, WORKER_THREAD };
}
