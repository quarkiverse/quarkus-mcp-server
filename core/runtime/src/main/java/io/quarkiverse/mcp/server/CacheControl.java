package io.quarkiverse.mcp.server;

/**
 * Caching hints for MCP results.
 * <p>
 * {@code ttlMs} is a freshness hint (in milliseconds) indicating how long clients may consider the result fresh before
 * re-fetching. If {@code 0}, the response should be considered immediately stale. If negative, the value is treated as unset.
 * <p>
 * {@code cacheScope} controls whether shared intermediaries may cache the response.
 *
 * @param ttlMs time-to-live in milliseconds
 * @param cacheScope the cache scope
 * @see CacheScope
 */
public record CacheControl(long ttlMs, CacheScope cacheScope) {

    public CacheControl {
        if (cacheScope == null) {
            throw new IllegalArgumentException("cacheScope must not be null");
        }
    }

}
