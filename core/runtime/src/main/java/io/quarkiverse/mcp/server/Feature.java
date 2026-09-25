package io.quarkiverse.mcp.server;

/**
 * Represents the kind of a feature method.
 */
public enum Feature {
    PROMPT,
    TOOL,
    RESOURCE,
    RESOURCE_TEMPLATE,
    PROMPT_COMPLETE,
    RESOURCE_TEMPLATE_COMPLETE,
    NOTIFICATION,
    EXTENSION_METHOD
}
