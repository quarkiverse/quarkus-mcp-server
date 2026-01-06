package io.quarkiverse.mcp.server;

import java.util.List;

/**
 * An MCP server/client implementation.
 *
 * @param name (must not be {@code null})
 * @param version (must not be {@code null})
 * @param title
 * @param icons (must not be {@code null})
 * @param description
 * @param websiteUrl
 */
public record Implementation(String name, String version, String title, List<Icon> icons, String description,
        String websiteUrl) {

    public Implementation(String name, String version, String title) {
        this(name, version, title, List.of(), null, null);
    }

    public Implementation {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        if (version == null) {
            throw new IllegalArgumentException("version must not be null");
        }
        if (icons == null) {
            throw new IllegalArgumentException("icons must not be null");
        }
    }

}
