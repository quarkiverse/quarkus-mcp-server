package io.quarkiverse.mcp.server.runtime;

/**
 * @deprecated use {@link io.quarkiverse.mcp.server.Feature} instead; this type is kept only for backward compatibility and
 *             will be removed in a future release
 */
@Deprecated(since = "2.1.0", forRemoval = true)
public enum Feature {
    PROMPT,
    TOOL,
    RESOURCE,
    RESOURCE_TEMPLATE,
    PROMPT_COMPLETE,
    RESOURCE_TEMPLATE_COMPLETE,
    NOTIFICATION,
    EXTENSION_METHOD;

    public boolean requiresUri() {
        return this == RESOURCE || this == RESOURCE_TEMPLATE;
    }
}
