package io.quarkiverse.mcp.server;

/**
 * Resource and resource template methods may accept the requested URI.
 *
 * @param value (must not be {@code null})
 * @see Resource
 * @see ResourceTemplate
 */
public record RequestUri(String value) {

    public RequestUri {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }
}
