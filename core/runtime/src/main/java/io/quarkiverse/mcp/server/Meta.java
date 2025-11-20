package io.quarkiverse.mcp.server;

import io.vertx.core.json.JsonObject;

/**
 * Additional metadata sent from the client to the server, i.e. the {@code _meta} part of the message.
 * <p>
 * All feature methods can accept this class as a parameter. It will be automatically injected before the
 * method is invoked.
 */
public interface Meta {

    /**
     *
     * @param key
     * @return the value for the given key, or {@code null}
     */
    Object getValue(MetaKey key);

    /**
     * If {@code _meta} is not present then an empty {@link JsonObject} is returned.
     *
     * @return the JSON representation of {@code _meta}, never {@code null}
     */
    JsonObject asJsonObject();

}
