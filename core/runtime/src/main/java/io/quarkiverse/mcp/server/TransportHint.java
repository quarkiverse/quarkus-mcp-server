package io.quarkiverse.mcp.server;

/**
 * Transport hints provide additional information to the transport layer about how a feature should be handled.
 *
 * @see FeatureManager.FeatureInfo#transportHints()
 * @see FeatureManager.TransportHintDefinition
 */
public enum TransportHint {

    /**
     * Hint to skip SSE initialization for the Streamable HTTP transport.
     * <p>
     * By default, features added programmatically always force SSE initialization because it's not possible to determine
     * whether SSE-dependent functionality will be used. If the feature handler does not use any of the following
     * SSE-dependent functionalities, this hint can be used to skip the SSE initialization and improve performance:
     * <ul>
     * <li>{@link Progress} notifications</li>
     * <li>{@link McpLog} messages</li>
     * <li>{@link Sampling} requests</li>
     * <li>{@link Roots} requests</li>
     * <li>{@link Elicitation} requests</li>
     * </ul>
     */
    STREAMABLE_HTTP_SKIP_SSE_INIT;
}
