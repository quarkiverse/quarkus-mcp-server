package io.quarkiverse.mcp.server;

/**
 * The name and version of an MCP implementation.
 *
 * @param name (must not be {@code null})
 * @param version (must not be {@code null})
 * @param title (may be {@code null})
 */
public record Implementation(String name, String version, String title) {

    public Implementation {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        if (version == null) {
            throw new IllegalArgumentException("version must not be null");
        }
    }

}
