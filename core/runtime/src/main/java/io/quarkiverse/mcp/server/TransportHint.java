package io.quarkiverse.mcp.server;

/**
 * Transport hints provide additional information to the transport layer about how a feature should be handled.
 *
 * @see FeatureManager.FeatureInfo#transportHints()
 * @see FeatureManager.TransportHintDefinition
 */
public enum TransportHint {

    /**
     * Hints the Streamable HTTP transport to skip the eager SSE initialization for the given feature. This is useful for
     * programmatic features that do not use SSE-dependent APIs such as {@link Progress}, {@link McpLog}, {@link Sampling},
     * {@link Roots}, or {@link Elicitation}, and the response should be sent as plain JSON instead of an SSE stream.
     * <p>
     * This hint is a no-op if {@code quarkus.mcp.server.http.streamable.lazy-sse-init=true} (the default), because SSE
     * initialization is already performed lazily.
     */
    STREAMABLE_HTTP_SKIP_SSE_INIT;
}
