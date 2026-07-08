package io.quarkiverse.mcp.server;

/**
 * Indicates a protocol error in a server feature method.
 * <p>
 * If a server feature method throws an exception that is an instance of {@link McpException} then it is
 * automatically converted to a JSON-RPC error message.
 *
 * @see JsonRpcErrorCodes
 */
public class McpException extends RuntimeException {

    private static final long serialVersionUID = -1270119918348689418L;

    private final int jsonRpcErrorCode;

    private final Object data;

    /**
     *
     * @param message
     * @param cause
     * @param jsonRpcErrorCode
     */
    public McpException(String message, Throwable cause, int jsonRpcErrorCode) {
        super(message, cause);
        this.jsonRpcErrorCode = jsonRpcErrorCode;
        this.data = null;
    }

    /**
     *
     * @param message
     * @param jsonRpcError
     */
    public McpException(String message, int jsonRpcError) {
        super(message);
        this.jsonRpcErrorCode = jsonRpcError;
        this.data = null;
    }

    /**
     *
     * @param message
     * @param jsonRpcErrorCode
     * @param data the optional structured data to include in the JSON-RPC error response
     */
    public McpException(String message, int jsonRpcErrorCode, Object data) {
        super(message);
        this.jsonRpcErrorCode = jsonRpcErrorCode;
        this.data = data;
    }

    /**
     *
     * @return the JSON-RPC error code
     * @see JsonRpcErrorCodes
     */
    public int getJsonRpcErrorCode() {
        return jsonRpcErrorCode;
    }

    /**
     *
     * @return the optional structured data to include in the JSON-RPC error response, or {@code null}
     */
    public Object getData() {
        return data;
    }

}
