package io.quarkiverse.mcp.server;

import java.util.Map;

/**
 * Abstract supertype for exceptions that are automatically converted to a JSON-RPC success result when thrown from a
 * server feature method (tool, prompt or resource).
 * <p>
 * Contrary to {@link McpException}, which is converted to a JSON-RPC error, a {@code McpResultException} supplies the
 * payload of a <em>successful</em> JSON-RPC response via {@link #result()}.
 *
 * @see McpResponseException
 * @see McpException
 */
public abstract class McpResultException extends McpResponseException {

    private static final long serialVersionUID = 1L;

    protected McpResultException(String message) {
        super(message);
    }

    protected McpResultException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Returns the payload serialized into the {@code result} field of the JSON-RPC response.
     * <p>
     * The returned object may be a {@link Map}, an {@code io.vertx.core.json.JsonObject} or any other object that can be
     * serialized to JSON.
     *
     * @return the JSON-RPC result payload, must not be {@code null}
     */
    public abstract Object result();

}
