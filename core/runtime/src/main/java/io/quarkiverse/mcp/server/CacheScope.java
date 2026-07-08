package io.quarkiverse.mcp.server;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Controls who may cache a response, analogous to HTTP {@code Cache-Control: public} vs {@code Cache-Control: private}.
 * <p>
 * {@link #PUBLIC} indicates that the response does not contain user-specific data and any client, shared gateway, or caching
 * proxy may store and serve the cached response to any user.
 * <p>
 * {@link #PRIVATE} indicates that the response contains private data. Cached responses may be reused for the same authorization
 * context but must not be shared across authorization contexts.
 */
public enum CacheScope {

    PUBLIC,
    PRIVATE;

    @JsonValue
    public String getName() {
        return toString().toLowerCase();
    }

}
