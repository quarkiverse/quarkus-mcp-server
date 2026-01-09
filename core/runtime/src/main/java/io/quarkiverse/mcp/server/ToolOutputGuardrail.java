package io.quarkiverse.mcp.server;

import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.smallrye.common.annotation.CheckReturnValue;
import io.smallrye.common.annotation.Experimental;
import io.smallrye.mutiny.Uni;

/**
 * Output guardrails can be used to validate and/or transform the result of a tool call.
 * <p>
 * Implementations must be CDI beans, or declare a public no-args constructor. In case of CDI, there must be exactly one bean
 * that has the implementation class in its set of bean types, otherwise the build fails. Qualifiers are ignored. Furthermore,
 * the context of the bean must be active during execution. If the scope is {@link jakarta.enterprise.context.Dependent} then
 * the bean instance is reused for all invocations of a given tool.
 * <p>
 * Execution of a guardrail must not block the caller thread unless blocking is allowed. An implementation class can be
 * annotated with {@link SupportedExecutionModels} to supply the list of supported execution models. If a tool declares a
 * guardrail with unsupported execution model then the build fails. By default, a guardrail should support all execution models.
 * <p>
 * An implementation can inspect the execution model of the current tool with {@link ToolInfo#executionModel()}, or use
 * {@link io.quarkus.runtime.BlockingOperationControl#isBlockingAllowed()} to detect if blocking is allowed on the current
 * thread. If blocking is not allowed but an implementation still needs to perform a blocking operation then it has to offload
 * the execution on a worker thread.
 *
 * @see SupportedExecutionModels
 * @see ToolInputGuardrail
 */
@Experimental("This API is experimental and may change in the future")
public interface ToolOutputGuardrail {

    /**
     * Note that the container always calls {@link #applyAsync(ToolOutputContext)} that delegates to
     * {@link #apply(ToolOutputContext)} by default.
     *
     * @param context
     * @see ToolCallException
     */
    default void apply(ToolOutputContext context) {
    }

    /**
     * An implemetation can replace the current response with {@link ToolOutputContext#setResponse(ToolResponse)}.
     *
     * @param context
     * @return the uni
     * @see ToolCallException
     */
    @CheckReturnValue
    default Uni<Void> applyAsync(ToolOutputContext context) {
        apply(context);
        return Uni.createFrom().voidItem();
    }

    interface ToolOutputContext {

        /**
         * @return the current tool
         */
        ToolInfo getTool();

        /**
         * @return the current connection
         */
        McpConnection getConnection();

        /**
         * @return the id of the current request
         */
        RequestId getRequestId();

        /**
         * @return the {@code _meta} part of the message
         */
        Meta getMeta();

        /**
         * @return the current response
         */
        ToolResponse getResponse();

        /**
         * Set the current response.
         *
         * @param response
         */
        void setResponse(ToolResponse response);

    }

}
