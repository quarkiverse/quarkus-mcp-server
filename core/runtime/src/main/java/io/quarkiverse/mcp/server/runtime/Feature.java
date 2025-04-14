package io.quarkiverse.mcp.server.runtime;

public enum Feature {
    PROMPT,
    TOOL,
    RESOURCE,
    RESOURCE_TEMPLATE,
    PROMPT_COMPLETE,
    RESOURCE_TEMPLATE_COMPLETE,
    NOTIFICATION;

    public boolean requiresUri() {
        return this == RESOURCE || this == RESOURCE_TEMPLATE;
    }
}