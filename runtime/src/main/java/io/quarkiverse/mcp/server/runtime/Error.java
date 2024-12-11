package io.quarkiverse.mcp.server.runtime;

import java.util.Map;

public record Error(int code, String message, Map<String, Object> data) {

    public static final Error UNSUPPORTED_PROTOCOL = new Error(1, "Unsupported protocol version");

    public Error(int code, String message) {
        this(code, message, Map.of());
    }

}
