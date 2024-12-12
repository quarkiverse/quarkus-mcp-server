package io.quarkiverse.mcp.server.runtime;

public record PromptArgument(String name, String description, boolean required, java.lang.reflect.Type type,
        Provider provider) {

    public enum Provider {
        PARAMS,
        REQUEST_ID,
        MCP_CONNECTION,
    }
}