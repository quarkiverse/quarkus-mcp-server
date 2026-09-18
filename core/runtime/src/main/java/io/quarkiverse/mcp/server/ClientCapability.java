package io.quarkiverse.mcp.server;

import java.util.Map;

/**
 * A capability supported by the client.
 */
public record ClientCapability(String name, Map<String, Object> properties) {

    public static final String ROOTS = "roots";

    public static final String SAMPLING = "sampling";

    public static final String ELICITATION = "elicitation";

    /**
     * The capability whose properties are the <a href="https://modelcontextprotocol.io/extensions/overview">MCP
     * extensions</a> declared by the client, keyed by extension id.
     */
    public static final String EXTENSIONS = "extensions";

}
