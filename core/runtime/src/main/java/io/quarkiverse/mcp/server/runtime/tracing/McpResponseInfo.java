package io.quarkiverse.mcp.server.runtime.tracing;

public class McpResponseInfo {

    private final boolean toolError;
    private final Integer jsonRpcErrorCode;
    private final String jsonRpcErrorMessage;

    public McpResponseInfo(boolean toolError, Integer jsonRpcErrorCode, String jsonRpcErrorMessage) {
        this.toolError = toolError;
        this.jsonRpcErrorCode = jsonRpcErrorCode;
        this.jsonRpcErrorMessage = jsonRpcErrorMessage;
    }

    public boolean toolError() {
        return toolError;
    }

    public Integer jsonRpcErrorCode() {
        return jsonRpcErrorCode;
    }

    public String jsonRpcErrorMessage() {
        return jsonRpcErrorMessage;
    }
}
