package io.quarkiverse.mcp.server;

/**
 * Caching hints for MCP results.
 * <p>
 * {@code ttlMs} is a freshness hint (in milliseconds) indicating how long clients may consider the result fresh before
 * re-fetching. If {@code 0}, the response should be considered immediately stale. Must not be negative.
 * <p>
 * {@code cacheScope} controls whether shared intermediaries may cache the response.
 *
 * @param ttlMs time-to-live in milliseconds; must not be negative
 * @param cacheScope the cache scope
 * @see CacheScope
 */
public record CacheControl(long ttlMs, CacheScope cacheScope) {

    public CacheControl {
        if (ttlMs < 0) {
            throw new IllegalArgumentException("ttlMs must not be negative");
        }
        if (cacheScope == null) {
            throw new IllegalArgumentException("cacheScope must not be null");
        }
    }

}
