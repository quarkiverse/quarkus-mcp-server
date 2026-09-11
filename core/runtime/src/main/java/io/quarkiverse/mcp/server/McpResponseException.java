package io.quarkiverse.mcp.server;

/**
 * Abstract supertype for exceptions that, when thrown from a server feature method (tool, prompt or resource), are
 * automatically converted to a JSON-RPC response instead of being treated as an internal error.
 * <p>
 * There are two kinds of response:
 * <ul>
 * <li>an {@linkplain McpException error response} - the exception carries a JSON-RPC error code, and</li>
 * <li>a {@linkplain McpResultException success result} - the exception supplies the JSON-RPC result payload.</li>
 * </ul>
 * This type is {@code sealed}: extensions must subclass one of the two branches - {@link McpException} or
 * {@link McpResultException} - rather than this class directly. Such subtypes are handled automatically, without any
 * additional registration.
 *
 * @see McpException
 * @see McpResultException
 */
public abstract sealed class McpResponseException extends RuntimeException permits McpException, McpResultException {

    private static final long serialVersionUID = 1L;

    protected McpResponseException(String message) {
        super(message);
    }

    protected McpResponseException(String message, Throwable cause) {
        super(message, cause);
    }

}
