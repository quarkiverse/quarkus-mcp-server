package io.quarkiverse.mcp.server;

/**
 * Controls inclusion of {@code io.modelcontextprotocol/serverInfo} in the {@code _meta}
 * of every JSON-RPC result.
 */
public enum ResponseServerInfo {

    /**
     * Include only {@code name} and {@code version}.
     */
    LIGHT,

    /**
     * Include all available fields.
     */
    FULL,

    /**
     * Do not include server info in per-response {@code _meta}.
     */
    NONE
}
