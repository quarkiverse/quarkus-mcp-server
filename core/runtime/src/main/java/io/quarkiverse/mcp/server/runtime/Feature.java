package io.quarkiverse.mcp.server.runtime;

public enum Feature {
    PROMPT,
    TOOL,
    RESOURCE,
    RESOURCE_TEMPLATE,
    PROMPT_COMPLETE,
    RESOURCE_TEMPLATE_COMPLETE;

    public boolean requiresUri() {
        return this == RESOURCE || this == RESOURCE_TEMPLATE;
    }
}