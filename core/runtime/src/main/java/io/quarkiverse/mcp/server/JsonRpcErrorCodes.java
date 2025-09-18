package io.quarkiverse.mcp.server;

public final class JsonRpcErrorCodes {

    public static final int RESOURCE_NOT_FOUND = -32002;
    public static final int INTERNAL_ERROR = -32603;
    public static final int INVALID_PARAMS = -32602;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_REQUEST = -32600;
    public static final int PARSE_ERROR = -32700;
    public static final int SECURITY_ERROR = -32001;

    private JsonRpcErrorCodes() {
    }

}
