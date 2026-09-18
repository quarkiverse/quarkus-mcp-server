package io.quarkiverse.mcp.server.runtime;

import java.util.Map;

import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.ToolResponse;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Intercepts a {@code tools/call} request before the tool is executed.
 * <p>
 * This is an internal extension point used by MCP extensions that need to alter the standard handling of a tool call, e.g.
 * to respond with a different result shape and execute the tool asynchronously (MCP Tasks). Implementations are CDI beans.
 * Interceptors are only consulted for tools that exist for the server of the request and pass the tool filters.
 */
public interface ToolCallInterceptor {

    /**
     * @param toolCall the tool call
     * @return a future completed once the response is sent if this interceptor handled the call, or {@code null} to let the
     *         server proceed with the next interceptor or the standard handling
     */
    Future<Void> intercept(ToolCall toolCall);

    /**
     * An intercepted {@code tools/call} request.
     */
    interface ToolCall {

        /**
         * @return the tool, never {@code null}
         */
        ToolInfo tool();

        /**
         * @return the request
         */
        McpRequest mcpRequest();

        /**
         * @return the JSON-RPC request message
         */
        JsonObject message();

        /**
         * @return the JSON-RPC request id
         */
        Object requestId();

        /**
         * @return the {@code _meta} object merged into the response, or {@code null}
         */
        JsonObject responseMeta();

        /**
         * Proceeds with the standard handling, i.e. executes the tool on the current context and sends the response.
         *
         * @param customProviders the objects available to the tool via {@link ArgumentProviders#customProvider(Class)}, keyed
         *        by type; may be empty
         * @return a future completed once the response is sent
         */
        Future<Void> proceed(Map<Class<?>, Object> customProviders);

        /**
         * Executes the tool on a new duplicated Vert.x context with its own request context, i.e. independently of the
         * lifecycle of the intercepted request. No response is sent.
         *
         * @param customProviders the objects available to the tool via {@link ArgumentProviders#customProvider(Class)}, keyed
         *        by type; may be empty
         * @return a future completed with the tool response, or failed with the execution failure
         */
        Future<ToolResponse> executeDetached(Map<Class<?>, Object> customProviders);

        /**
         * Converts an execution failure into the JSON-RPC response message the standard handling would send, i.e. a
         * {@code result} for an {@link io.quarkiverse.mcp.server.McpResultException}, or an {@code error} otherwise.
         *
         * @param cause the execution failure
         * @return the JSON-RPC response message, or {@code null} if the failure represents a cancelled operation
         */
        JsonObject failureResponse(Throwable cause);

    }

}
